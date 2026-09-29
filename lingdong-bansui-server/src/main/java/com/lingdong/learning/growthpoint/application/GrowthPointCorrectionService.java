package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.growthpoint.domain.GrowthPointLedger;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskCheckIn;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCheckInMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 在一个事务中完成积分冲销、打卡重开、任务回退和审计留痕。 */
@Service
public class GrowthPointCorrectionService {
    private static final String FEATURE_CODE = "GROWTH_POINT_CORRECTION";
    private static final long CORRECTION_WINDOW_HOURS = 72L;

    private final GrowthPointLedgerMapper ledgerMapper;
    private final GrowthPointAccountMapper accountMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskCheckInMapper checkInMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public GrowthPointCorrectionService(
            GrowthPointLedgerMapper ledgerMapper,
            GrowthPointAccountMapper accountMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            TaskCheckInMapper checkInMapper,
            TaskAssignmentEventMapper eventMapper,
            ParentStudentMapper parentStudentMapper,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.ledgerMapper = ledgerMapper;
        this.accountMapper = accountMapper;
        this.assignmentMapper = assignmentMapper;
        this.checkInMapper = checkInMapper;
        this.eventMapper = eventMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public GrowthPointCorrectionResult correct(
            AuthenticatedUser currentUser,
            Long studentId,
            CorrectGrowthPointCommand command
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireWebParent(currentUser);
        Long validatedStudentId = requireId(studentId, "学生标识不合法");
        if (!parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), validatedStudentId)) {
            throw new ResourceNotFoundException("学生积分账户不存在或不可访问");
        }
        Long originalLedgerId = requireId(
                command == null ? null : command.originalLedgerId(), "原积分台账标识不合法");
        String reason = normalizeReason(command == null ? null : command.reason());

        GrowthPointLedger original = ledgerMapper.findByIdForUpdate(originalLedgerId);
        if (original == null || !validatedStudentId.equals(original.studentId())) {
            throw new ResourceNotFoundException("原积分台账不存在或不可访问");
        }
        requireCorrectableOriginal(currentUser, original);
        if (ledgerMapper.findCorrectionIdByOriginalId(original.id()) != null) {
            throw new IllegalStateException("原积分台账已纠错");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (now.isBefore(original.occurredAt())
                || now.isAfter(original.occurredAt().plusHours(CORRECTION_WINDOW_HOURS))) {
            throw new IllegalStateException("已超过积分纠错时限");
        }

        TaskAssignmentStateRow assignment = assignmentMapper.findStateByIdAndStudentIdForUpdate(
                original.sourceAssignmentId(), validatedStudentId);
        if (assignment == null || assignment.currentStatus() != TaskAssignmentStatus.COMPLETED
                || !currentUser.userId().equals(assignment.currentReviewerId())) {
            throw new IllegalStateException("任务状态不允许积分纠错");
        }
        TaskCheckIn checkIn = checkInMapper.findLatestApprovedForUpdate(assignment.id());
        if (checkIn == null || !currentUser.userId().equals(checkIn.reviewedByUserId())) {
            throw new IllegalStateException("不存在本人审核通过的打卡");
        }
        GrowthPointAccount account = accountMapper.findByStudentIdForUpdate(validatedStudentId);
        if (account == null || !account.id().equals(original.accountId())
                || account.totalPoints() < original.amount()) {
            throw new IllegalStateException("学生积分账户无法完成纠错");
        }

        long correctedPoints = original.amount();
        long availableDeduction = Math.min(account.availablePoints(), correctedPoints);
        Long correctionLedgerId = idGenerator.nextId();
        requireSingleWrite(accountMapper.applyCorrection(
                account.id(), correctedPoints, availableDeduction, account.versionNo(), now));
        requireSingleWrite(ledgerMapper.insert(GrowthPointLedger.correction(
                correctionLedgerId, original, availableDeduction, currentUser.userId(), now, reason)));
        requireSingleWrite(checkInMapper.reopenApproved(checkIn.id(), currentUser.userId(), now));
        requireSingleWrite(assignmentMapper.transitionStatus(
                assignment.id(), TaskAssignmentStatus.COMPLETED.name(),
                TaskAssignmentStatus.PENDING_REVIEW.name(), assignment.versionNo(), now, null));
        requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), assignment.id(), TaskAssignmentEventType.POINT_CORRECTED,
                currentUser.userId(), TaskAssignmentStatus.COMPLETED,
                TaskAssignmentStatus.PENDING_REVIEW, reason,
                "原奖励台账=" + original.id() + ";纠错台账=" + correctionLedgerId, now)));

        return new GrowthPointCorrectionResult(
                validatedStudentId, assignment.id(), original.id(), correctionLedgerId,
                correctedPoints, account.totalPoints() - correctedPoints,
                account.availablePoints() - availableDeduction,
                TaskAssignmentStatus.PENDING_REVIEW, now);
    }

    private void requireCorrectableOriginal(AuthenticatedUser currentUser, GrowthPointLedger original) {
        if (original.changeType() != GrowthPointChangeType.TASK_REWARD
                || original.sourceType() != LearningTaskSourceType.FAMILY
                || original.sourceAssignmentId() == null
                || original.amount() == null || original.amount() <= 0
                || !currentUser.userId().equals(original.reviewerUserId())) {
            throw new IllegalStateException("该积分台账不允许纠错");
        }
    }

    private void requireWebParent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new SystemOperationAccessDeniedException("仅 Web 端主家长可发起积分纠错");
        }
    }

    private Long requireId(Long value, String message) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("纠错原因不合法");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("积分纠错状态已变化");
        }
    }
}
