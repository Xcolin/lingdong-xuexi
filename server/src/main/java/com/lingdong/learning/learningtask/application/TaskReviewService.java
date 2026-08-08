package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.attachment.application.TaskAttachmentApplicationService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthPointAccount;
import com.lingdong.learning.growthpoint.domain.GrowthPointLedger;
import com.lingdong.learning.growthpoint.application.GrowthPointAwardCalculation;
import com.lingdong.learning.growthpoint.application.GrowthPointDecayService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountMapper;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerMapper;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskCheckIn;
import com.lingdong.learning.learningtask.domain.ReviewerTransfer;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCheckInMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskReviewerMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ReviewerTransferMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 当前审核人的待办查询、状态处理与任务奖励积分事务。 */
@Service
public class TaskReviewService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final Set<String> REVIEW_ROLES = Set.of("PARENT", "TEACHER", "ORG_ADMIN");
    private static final Set<Integer> ALLOWED_BASE_POINTS = Set.of(10, 20, 30);

    private final TaskReviewMapper reviewMapper;
    private final TaskCheckInMapper checkInMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final TaskReviewerMapper reviewerMapper;
    private final ReviewerTransferMapper transferMapper;
    private final GrowthPointAccountMapper pointAccountMapper;
    private final GrowthPointLedgerMapper pointLedgerMapper;
    private final GrowthPointDecayService pointDecayService;
    private final FeatureAccessService featureAccessService;
    private final TaskAttachmentApplicationService attachmentService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public TaskReviewService(
            TaskReviewMapper reviewMapper,
            TaskCheckInMapper checkInMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            TaskAssignmentEventMapper eventMapper,
            TaskReviewerMapper reviewerMapper,
            ReviewerTransferMapper transferMapper,
            GrowthPointAccountMapper pointAccountMapper,
            GrowthPointLedgerMapper pointLedgerMapper,
            GrowthPointDecayService pointDecayService,
            FeatureAccessService featureAccessService,
            TaskAttachmentApplicationService attachmentService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.reviewMapper = reviewMapper;
        this.checkInMapper = checkInMapper;
        this.assignmentMapper = assignmentMapper;
        this.eventMapper = eventMapper;
        this.reviewerMapper = reviewerMapper;
        this.transferMapper = transferMapper;
        this.pointAccountMapper = pointAccountMapper;
        this.pointLedgerMapper = pointLedgerMapper;
        this.pointDecayService = pointDecayService;
        this.featureAccessService = featureAccessService;
        this.attachmentService = attachmentService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TaskReviewPage findPage(AuthenticatedUser currentUser, int page, int pageSize) {
        requireReviewer(currentUser);
        int normalizedPage = requireRange(page, "页码", 1, 1_000_000);
        int normalizedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        return new TaskReviewPage(
                reviewMapper.findPage(currentUser.userId(),
                                (normalizedPage - 1) * normalizedPageSize, normalizedPageSize)
                        .stream().map(this::toView).toList(),
                normalizedPage, normalizedPageSize, reviewMapper.count(currentUser.userId()));
    }

    @Transactional(readOnly = true)
    public TaskReviewView findById(AuthenticatedUser currentUser, Long assignmentId) {
        requireReviewer(currentUser);
        TaskReviewRow row = reviewMapper.findByAssignmentIdAndReviewer(
                requireId(assignmentId), currentUser.userId());
        if (row == null) {
            throw new ResourceNotFoundException("审核任务不存在或不可访问");
        }
        return toView(row);
    }

    @Transactional
    public TaskReviewActionResult reject(
            AuthenticatedUser currentUser,
            Long assignmentId,
            RejectTaskCheckInCommand command
    ) {
        requireReviewer(currentUser);
        String comment = normalizeComment(command == null ? null : command.reviewComment());
        TaskReviewStateRow state = reviewMapper.findStateForUpdate(
                requireId(assignmentId), currentUser.userId());
        if (state == null) {
            throw new ResourceNotFoundException("审核任务不存在或不可访问");
        }
        if (state.currentStatus() != TaskAssignmentStatus.PENDING_REVIEW) {
            throw new IllegalStateException("任务不在待审核状态");
        }
        TaskCheckIn checkIn = checkInMapper.findLatestSubmittedForUpdate(state.assignmentId());
        if (checkIn == null) {
            throw new IllegalStateException("不存在待审核打卡");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireSingleWrite(checkInMapper.reject(checkIn.id(), currentUser.userId(), now, comment));
        requireSingleWrite(assignmentMapper.transitionStatus(
                state.assignmentId(), TaskAssignmentStatus.PENDING_REVIEW.name(),
                TaskAssignmentStatus.IN_PROGRESS.name(), state.versionNo(), now, null));
        requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), state.assignmentId(), TaskAssignmentEventType.REVIEW_REJECTED,
                currentUser.userId(), TaskAssignmentStatus.PENDING_REVIEW,
                TaskAssignmentStatus.IN_PROGRESS, comment, null, now)));
        return new TaskReviewActionResult(
                state.assignmentId(), TaskAssignmentStatus.IN_PROGRESS.name(),
                checkIn.id(), "REJECTED");
    }

    /** 审核通过、任务完成和积分奖励必须在同一事务中提交。 */
    @Transactional
    public ApproveTaskReviewResult approve(AuthenticatedUser currentUser, Long assignmentId) {
        requireReviewer(currentUser);
        TaskReviewStateRow state = reviewMapper.findStateForUpdate(
                requireId(assignmentId), currentUser.userId());
        if (state == null) {
            throw new ResourceNotFoundException("审核任务不存在或不可访问");
        }
        if (state.currentStatus() != TaskAssignmentStatus.PENDING_REVIEW) {
            throw new IllegalStateException("任务不在待审核状态");
        }
        if (state.basePoints() == null || !ALLOWED_BASE_POINTS.contains(state.basePoints())) {
            throw new IllegalStateException("任务基础积分不合法");
        }
        TaskCheckIn checkIn = checkInMapper.findLatestSubmittedForUpdate(state.assignmentId());
        if (checkIn == null) {
            throw new IllegalStateException("不存在待审核打卡");
        }

        GrowthPointAccount account = pointAccountMapper.findByStudentIdForUpdate(state.studentId());
        if (account == null) {
            requireSingleWrite(pointAccountMapper.insertInitial(state.studentId()));
            account = pointAccountMapper.findByStudentIdForUpdate(state.studentId());
        }
        if (account == null) {
            throw new IllegalStateException("学生积分账户不可用");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        GrowthPointAwardCalculation award = pointDecayService.calculate(
                state.studentId(), state.taskId(), state.sourceType(), state.scheduledDate(),
                state.basePoints(), now);
        long awardedPoints = award.awardedPoints();
        long totalPoints = Math.addExact(account.totalPoints(), awardedPoints);
        long availablePoints = Math.addExact(account.availablePoints(), awardedPoints);
        Long ledgerId = idGenerator.nextId();

        requireSingleWrite(checkInMapper.approve(checkIn.id(), currentUser.userId(), now));
        requireSingleWrite(pointAccountMapper.addTaskReward(
                account.id(), awardedPoints, account.versionNo(), now));
        requireSingleWrite(pointLedgerMapper.insert(GrowthPointLedger.taskReward(
                ledgerId, account.id(), state.studentId(), state.assignmentId(), state.taskId(),
                state.sourceType(), state.sourceOrganizationId(), award.basePoints(), awardedPoints,
                award.streakDays(), award.decayPercent(), award.decayRuleId(),
                currentUser.userId(), now)));
        requireSingleWrite(assignmentMapper.transitionStatus(
                state.assignmentId(), TaskAssignmentStatus.PENDING_REVIEW.name(),
                TaskAssignmentStatus.COMPLETED.name(), state.versionNo(), now, null));
        requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), state.assignmentId(), TaskAssignmentEventType.REVIEW_APPROVED,
                currentUser.userId(), TaskAssignmentStatus.PENDING_REVIEW,
                TaskAssignmentStatus.COMPLETED, null, null, now)));

        return new ApproveTaskReviewResult(
                state.assignmentId(), TaskAssignmentStatus.COMPLETED.name(), checkIn.id(),
                "APPROVED", awardedPoints, totalPoints, availablePoints, ledgerId);
    }

    @Transactional(readOnly = true)
    public List<ReviewerOption> findReviewerOptions(
            AuthenticatedUser currentUser, Long assignmentId
    ) {
        requireReviewer(currentUser);
        TaskReviewRow row = reviewMapper.findByAssignmentIdAndReviewer(
                requireId(assignmentId), currentUser.userId());
        if (row == null) {
            throw new ResourceNotFoundException("审核任务不存在或不可访问");
        }
        return reviewerMapper.findOptions(
                        row.sourceType(), row.sourceOrganizationId(), row.studentId(), currentUser.userId())
                .stream().map(option -> new ReviewerOption(option.userId(), option.displayName()))
                .toList();
    }

    @Transactional
    public ReviewerTransferResult transfer(
            AuthenticatedUser currentUser,
            Long assignmentId,
            TransferTaskReviewCommand command
    ) {
        requireReviewer(currentUser);
        Long nextReviewerId = requireId(command == null ? null : command.reviewerUserId());
        String reason = normalizeReason(command == null ? null : command.transferReason());
        TaskReviewStateRow state = reviewMapper.findStateForUpdate(
                requireId(assignmentId), currentUser.userId());
        if (state == null) {
            throw new ResourceNotFoundException("审核任务不存在或不可访问");
        }
        if (state.currentStatus() != TaskAssignmentStatus.PENDING_REVIEW) {
            throw new IllegalStateException("任务不在待审核状态");
        }
        boolean candidateAllowed = reviewerMapper.findOptions(
                        state.sourceType(), state.sourceOrganizationId(), state.studentId(),
                        state.currentReviewerId())
                .stream().anyMatch(option -> option.userId().equals(nextReviewerId));
        if (!candidateAllowed) {
            throw new IllegalArgumentException("目标审核人不在可选范围内");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireSingleWrite(assignmentMapper.transferReviewer(
                state.assignmentId(), state.currentReviewerId(), nextReviewerId,
                state.versionNo(), now));
        requireSingleWrite(transferMapper.insert(new ReviewerTransfer(
                idGenerator.nextId(), state.assignmentId(), state.currentReviewerId(),
                nextReviewerId, currentUser.userId(), reason, now)));
        requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), state.assignmentId(), TaskAssignmentEventType.REVIEWER_TRANSFERRED,
                currentUser.userId(), TaskAssignmentStatus.PENDING_REVIEW,
                TaskAssignmentStatus.PENDING_REVIEW, reason, null, now)));
        return new ReviewerTransferResult(state.assignmentId(), nextReviewerId);
    }

    private void requireReviewer(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (currentUser == null || currentUser.roleCodes().stream().noneMatch(REVIEW_ROLES::contains)) {
            throw new SystemOperationAccessDeniedException("当前用户不是业务审核角色");
        }
    }

    private TaskReviewView toView(TaskReviewRow row) {
        TaskCheckInView checkIn = new TaskCheckInView(
                row.checkInId(), row.submissionNo(), row.checkInContent(), row.checkInStatus(),
                row.submittedAt(), row.reviewComment(),
                attachmentService.findByCheckInId(row.checkInId()));
        return new TaskReviewView(
                row.assignmentId(), row.taskId(), row.title(), row.basePoints(),
                row.studentId(), row.studentName(),
                row.sourceType(), row.sourceOrganizationId(), row.sourceOrganizationName(),
                row.currentStatus(), row.currentReviewerId(), row.reviewerDisplayName(), checkIn);
    }

    private Long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("任务实例标识不合法");
        }
        return value;
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }

    private String normalizeComment(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("审核意见不合法");
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("转交原因不合法");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("审核任务状态已变化");
        }
    }
}
