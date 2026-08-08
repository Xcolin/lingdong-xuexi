package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointAccountViewRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointLedgerViewRow;
import com.lingdong.learning.growthpoint.infrastructure.persistence.GrowthPointQueryMapper;
import com.lingdong.learning.learningtask.application.CurrentStudentAccessService;
import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.student.domain.Student;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 积分账户与台账安全查询服务。
 *
 * <p>统一账户包含家庭、机构和教师来源，因此只向学生本人和活动主关系家长开放。</p>
 */
@Service
public class GrowthPointQueryService {
    private static final String FEATURE_CODE = "GROWTH_POINT_QUERY";
    private static final String CORRECTION_FEATURE_CODE = "GROWTH_POINT_CORRECTION";
    private static final long CORRECTION_WINDOW_HOURS = 72L;

    private final GrowthPointQueryMapper queryMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final ParentStudentMapper parentStudentMapper;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;

    public GrowthPointQueryService(
            GrowthPointQueryMapper queryMapper,
            CurrentStudentAccessService currentStudentAccessService,
            ParentStudentMapper parentStudentMapper,
            FeatureAccessService featureAccessService,
            Clock clock
    ) {
        this.queryMapper = queryMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.parentStudentMapper = parentStudentMapper;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GrowthPointAccountView findMyAccount(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        return requireAccount(student.id());
    }

    @Transactional(readOnly = true)
    public GrowthPointLedgerPage findMyLedgers(
            AuthenticatedUser currentUser, int page, int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Student student = requireMiniappStudent(currentUser);
        return findLedgerPage(student.id(), page, pageSize, null);
    }

    @Transactional(readOnly = true)
    public List<GrowthPointStudentOption> findParentStudents(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireWebParent(currentUser);
        return queryMapper.findPrimaryStudentsByParentUserId(currentUser.userId()).stream()
                .map(row -> new GrowthPointStudentOption(row.studentId(), row.studentName()))
                .toList();
    }

    @Transactional(readOnly = true)
    public GrowthPointAccountView findChildAccount(
            AuthenticatedUser currentUser, Long studentId
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireAccessibleChild(currentUser, studentId);
        return requireAccount(studentId);
    }

    @Transactional(readOnly = true)
    public GrowthPointLedgerPage findChildLedgers(
            AuthenticatedUser currentUser, Long studentId, int page, int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        requireAccessibleChild(currentUser, studentId);
        return findLedgerPage(studentId, page, pageSize, currentUser.userId());
    }

    private Student requireMiniappStudent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.MINIAPP) {
            throw new SystemOperationAccessDeniedException("仅小程序学生可查询本人积分");
        }
        return currentStudentAccessService.require(currentUser);
    }

    private void requireAccessibleChild(AuthenticatedUser currentUser, Long studentId) {
        requireWebParent(currentUser);
        if (studentId == null || !parentStudentMapper.existsActivePrimaryByParentAndStudent(
                currentUser.userId(), studentId)) {
            throw new ResourceNotFoundException("学生积分账户不存在或不可访问");
        }
    }

    private void requireWebParent(AuthenticatedUser currentUser) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB
                || !currentUser.roleCodes().contains("PARENT")) {
            throw new SystemOperationAccessDeniedException("仅 Web 端主家长可查询孩子积分");
        }
    }

    private GrowthPointAccountView requireAccount(Long studentId) {
        GrowthPointAccountViewRow row = queryMapper.findAccountByStudentId(studentId);
        if (row == null) {
            throw new ResourceNotFoundException("学生积分账户不存在或不可访问");
        }
        return new GrowthPointAccountView(
                row.studentId(), row.studentName(), row.totalPoints(), row.availablePoints(), row.updatedAt());
    }

    private GrowthPointLedgerPage findLedgerPage(
            Long studentId, int page, int pageSize, Long correctionActorUserId
    ) {
        int validatedPage = requireRange(page, "页码", 1, 1_000_000);
        int validatedPageSize = requireRange(pageSize, "每页数量", 1, 100);
        LocalDateTime now = LocalDateTime.now(clock);
        boolean correctionEnabled = correctionActorUserId != null
                && featureAccessService.isEnabled(CORRECTION_FEATURE_CODE, null);
        List<GrowthPointLedgerView> items = queryMapper.findLedgersByStudentId(
                        studentId, (validatedPage - 1) * validatedPageSize, validatedPageSize).stream()
                .map(row -> toView(row, correctionActorUserId, correctionEnabled, now))
                .toList();
        return new GrowthPointLedgerPage(
                items, validatedPage, validatedPageSize, queryMapper.countLedgersByStudentId(studentId));
    }

    private GrowthPointLedgerView toView(
            GrowthPointLedgerViewRow row,
            Long correctionActorUserId,
            boolean correctionEnabled,
            LocalDateTime now
    ) {
        LocalDateTime correctionDeadline = row.changeType() == GrowthPointChangeType.TASK_REWARD
                && row.sourceType() == LearningTaskSourceType.FAMILY
                ? row.occurredAt().plusHours(CORRECTION_WINDOW_HOURS) : null;
        boolean correctable = correctionEnabled
                && row.changeType() == GrowthPointChangeType.TASK_REWARD
                && row.sourceType() == LearningTaskSourceType.FAMILY
                && correctionActorUserId.equals(row.reviewerUserId())
                && row.correctionLedgerId() == null
                && row.assignmentStatus() == TaskAssignmentStatus.COMPLETED
                && correctionActorUserId.equals(row.assignmentReviewerUserId())
                && !now.isBefore(row.occurredAt())
                && !now.isAfter(correctionDeadline);
        return new GrowthPointLedgerView(
                row.id(), row.changeType(), row.amount(), row.availableDelta(),
                row.sourceAssignmentId(), row.sourceExchangeId(), row.sourceTaskId(),
                row.basePointsSnapshot(), row.decayPercent(), row.streakDays(), row.decayRuleId(),
                row.sourceType(), row.sourceOrganizationId(),
                row.sourceOrganizationName(), row.taskTitle(), row.reviewerUserId(),
                row.reviewerDisplayName(), row.occurredAt(), row.remark(),
                row.correctionOfId(), row.correctionLedgerId(), correctionDeadline, correctable);
    }

    private int requireRange(int value, String fieldName, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return value;
    }
}
