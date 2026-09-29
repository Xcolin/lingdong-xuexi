package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ManagedTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.ManagedTaskAssignmentStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskPauseMapper;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 按任务来源和组织数据范围设置免执行，并保留状态审计。 */
@Service
public class ManagedTaskAssignmentService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";

    private final ManagedTaskAssignmentMapper managedMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskPauseMapper pauseMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public ManagedTaskAssignmentService(
            ManagedTaskAssignmentMapper managedMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            TaskPauseMapper pauseMapper,
            TaskAssignmentEventMapper eventMapper,
            ParentStudentMapper parentStudentMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.managedMapper = managedMapper;
        this.assignmentMapper = assignmentMapper;
        this.pauseMapper = pauseMapper;
        this.eventMapper = eventMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public ManagedTaskAssignmentActionResult exempt(
            AuthenticatedUser currentUser,
            Long assignmentId,
            ExemptTaskAssignmentCommand command
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        Long normalizedId = requireId(assignmentId);
        String reason = normalizeReason(command == null ? null : command.reason());
        ManagedTaskAssignmentStateRow state = managedMapper.findStateForUpdate(normalizedId);
        if (state == null || !canManage(currentUser, state)) {
            throw new ResourceNotFoundException("任务实例不存在或不可管理");
        }
        if (state.currentStatus() != TaskAssignmentStatus.PENDING_CLAIM
                && state.currentStatus() != TaskAssignmentStatus.IN_PROGRESS) {
            throw new IllegalStateException("任务状态不允许免执行");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        pauseMapper.terminateActive(state.assignmentId(), now);
        requireSingleWrite(assignmentMapper.transitionStatus(
                state.assignmentId(), state.currentStatus().name(),
                TaskAssignmentStatus.EXEMPT.name(), state.versionNo(), now, null));
        requireSingleWrite(eventMapper.insert(new TaskAssignmentEvent(
                idGenerator.nextId(), state.assignmentId(), TaskAssignmentEventType.EXEMPTED,
                currentUser.userId(), state.currentStatus(), TaskAssignmentStatus.EXEMPT,
                reason, null, now)));
        return new ManagedTaskAssignmentActionResult(
                state.assignmentId(), TaskAssignmentStatus.EXEMPT.name());
    }

    private boolean canManage(AuthenticatedUser currentUser, ManagedTaskAssignmentStateRow state) {
        if (currentUser == null) {
            return false;
        }
        if (state.sourceType() == LearningTaskSourceType.FAMILY) {
            return currentUser.roleCodes().contains("PARENT")
                    && parentStudentMapper.existsActivePrimaryByParentAndStudent(
                    currentUser.userId(), state.studentId());
        }
        boolean creatorTeacher = state.sourceType() == LearningTaskSourceType.TEACHER
                && currentUser.roleCodes().contains("TEACHER")
                && currentUser.userId().equals(state.creatorUserId());
        boolean scopedOrganizationAdministrator = currentUser.roleCodes().contains("ORG_ADMIN")
                && organizationDataScopeService.canAccess(
                currentUser.userId(), state.sourceOrganizationId());
        return creatorTeacher || scopedOrganizationAdministrator;
    }

    private Long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("任务实例标识不合法");
        }
        return value;
    }

    private String normalizeReason(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("免执行原因不合法");
        }
        return normalized;
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("任务实例状态已变化");
        }
    }
}
