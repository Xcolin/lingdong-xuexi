package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskTag;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskDeferHistory;
import com.lingdong.learning.learningtask.domain.TaskDeferType;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskTagMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferHistoryMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskDeferStateRow;
import com.lingdong.learning.student.infrastructure.persistence.ParentStudentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/** 按来源数据范围执行手动或自动顺延，并追加不可变历史。 */
@Service
public class TaskDeferService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime DUE_TIME = LocalTime.of(23, 59, 59);

    private final TaskDeferMapper deferMapper;
    private final LearningTaskMapper taskMapper;
    private final LearningTaskTagMapper tagMapper;
    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskDeferHistoryMapper historyMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final OrganizationDataScopeService organizationDataScopeService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public TaskDeferService(
            TaskDeferMapper deferMapper,
            LearningTaskMapper taskMapper,
            LearningTaskTagMapper tagMapper,
            LearningTaskAssignmentMapper assignmentMapper,
            TaskDeferHistoryMapper historyMapper,
            ParentStudentMapper parentStudentMapper,
            OrganizationDataScopeService organizationDataScopeService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.deferMapper = deferMapper;
        this.taskMapper = taskMapper;
        this.tagMapper = tagMapper;
        this.assignmentMapper = assignmentMapper;
        this.historyMapper = historyMapper;
        this.parentStudentMapper = parentStudentMapper;
        this.organizationDataScopeService = organizationDataScopeService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public TaskDeferResult deferManually(
            AuthenticatedUser currentUser, Long assignmentId, LocalDate targetDate
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LocalDate today = LocalDate.now(clock.withZone(BUSINESS_ZONE));
        validateManualTargetDate(targetDate, today);
        TaskDeferStateRow state = deferMapper.findStateForUpdate(requireId(assignmentId));
        if (state == null || !canManage(currentUser, state)) {
            throw new ResourceNotFoundException("任务实例不存在或不可管理");
        }
        boolean canDefer = state.currentStatus() == TaskAssignmentStatus.NEEDS_IMPROVEMENT
                || (state.currentStatus() == TaskAssignmentStatus.PENDING_CLAIM
                && state.lastDeferType() == TaskDeferType.AUTO);
        if (!canDefer) {
            throw new IllegalStateException("任务当前状态不允许顺延");
        }
        return defer(state, targetDate, TaskDeferType.MANUAL, currentUser.userId());
    }

    @Transactional
    public TaskDeferResult deferAutomatically(Long assignmentId, LocalDate targetDate) {
        TaskDeferStateRow state = deferMapper.findStateForUpdate(requireId(assignmentId));
        if (state == null
                || state.currentStatus() != TaskAssignmentStatus.NEEDS_IMPROVEMENT
                || state.lastDeferType() == TaskDeferType.MANUAL
                || targetDate == null
                || !targetDate.isAfter(state.scheduledDate())) {
            return null;
        }
        return defer(state, targetDate, TaskDeferType.AUTO, null);
    }

    @Transactional(readOnly = true)
    public ManagedDeferCandidatePage findManagedCandidates(
            AuthenticatedUser currentUser, int page, int pageSize
    ) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB) {
            throw new IllegalArgumentException("仅支持 Web 管理端查询可顺延任务");
        }
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("分页参数不合法");
        }
        ManagedDeferCandidateQuery query = new ManagedDeferCandidateQuery(
                currentUser.userId(), currentUser.roleCodes().contains("PARENT"),
                currentUser.roleCodes().contains("TEACHER"),
                currentUser.roleCodes().contains("ORG_ADMIN"),
                pageSize, (long) (page - 1) * pageSize);
        return new ManagedDeferCandidatePage(
                deferMapper.findManagedPage(query), page, pageSize, deferMapper.countManaged(query));
    }

    private TaskDeferResult defer(
            TaskDeferStateRow state,
            LocalDate targetDate,
            TaskDeferType deferType,
            Long operatorUserId
    ) {
        Long targetTaskId = idGenerator.nextId();
        if (taskMapper.insertDeferredCopy(targetTaskId, state.taskId(), targetDate) != 1) {
            throw new IllegalStateException("顺延任务配置复制失败");
        }
        copyTags(state.taskId(), targetTaskId);
        if (assignmentMapper.deferAssignment(
                state.assignmentId(), targetTaskId, state.currentStatus().name(),
                TaskAssignmentStatus.PENDING_CLAIM.name(), targetDate, targetDate.atTime(DUE_TIME),
                deferType.name(), operatorUserId, state.versionNo()) != 1) {
            throw new IllegalStateException("任务状态已变化，请重试");
        }
        LocalDateTime occurredAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        TaskDeferHistory history = new TaskDeferHistory(
                idGenerator.nextId(), state.assignmentId(), state.taskId(), targetTaskId,
                state.scheduledDate(), targetDate, deferType, operatorUserId, occurredAt);
        if (historyMapper.insert(history) != 1) {
            throw new IllegalStateException("任务顺延历史写入失败");
        }
        return new TaskDeferResult(
                state.assignmentId(), targetTaskId, TaskAssignmentStatus.PENDING_CLAIM.name(),
                targetDate, deferType, true);
    }

    private void copyTags(Long sourceTaskId, Long targetTaskId) {
        List<String> tagCodes = tagMapper.findCodesByTaskId(sourceTaskId);
        for (String tagCode : tagCodes) {
            if (tagMapper.insert(new LearningTaskTag(
                    idGenerator.nextId(), targetTaskId, tagCode, null)) != 1) {
                throw new IllegalStateException("顺延任务标签复制失败");
            }
        }
    }

    private boolean canManage(AuthenticatedUser currentUser, TaskDeferStateRow state) {
        if (currentUser == null || currentUser.clientType() != AuthClientType.WEB) {
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

    private void validateManualTargetDate(LocalDate targetDate, LocalDate today) {
        if (targetDate == null || !targetDate.isAfter(today) || targetDate.isAfter(today.plusDays(7))) {
            throw new IllegalArgumentException("顺延目标日期必须在未来 1 至 7 天内");
        }
    }

    private Long requireId(Long value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("任务实例标识不合法");
        }
        return value;
    }
}
