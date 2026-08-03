package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEvent;
import com.lingdong.learning.learningtask.domain.TaskAssignmentEventType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskCheckIn;
import com.lingdong.learning.learningtask.domain.TaskPause;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskAssignmentMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentEventMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskAssignmentStateRow;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskCheckInMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.TaskPauseMapper;
import com.lingdong.learning.student.domain.Student;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** 在任务实例行锁内完成学生执行状态、明细和审计事件的一致性写入。 */
@Service
public class StudentTaskExecutionService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final int MAX_PAUSE_MINUTES = 120;
    private static final int MAX_CHECKIN_CONTENT_LENGTH = 1_000;
    private static final int MAX_REASON_LENGTH = 500;

    private final LearningTaskAssignmentMapper assignmentMapper;
    private final TaskPauseMapper pauseMapper;
    private final TaskCheckInMapper checkInMapper;
    private final TaskAssignmentEventMapper eventMapper;
    private final CurrentStudentAccessService currentStudentAccessService;
    private final StudentTaskAssignmentService assignmentQueryService;
    private final FeatureAccessService featureAccessService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public StudentTaskExecutionService(
            LearningTaskAssignmentMapper assignmentMapper,
            TaskPauseMapper pauseMapper,
            TaskCheckInMapper checkInMapper,
            TaskAssignmentEventMapper eventMapper,
            CurrentStudentAccessService currentStudentAccessService,
            StudentTaskAssignmentService assignmentQueryService,
            FeatureAccessService featureAccessService,
            IdGenerator idGenerator,
            Clock clock
    ) {
        this.assignmentMapper = assignmentMapper;
        this.pauseMapper = pauseMapper;
        this.checkInMapper = checkInMapper;
        this.eventMapper = eventMapper;
        this.currentStudentAccessService = currentStudentAccessService;
        this.assignmentQueryService = assignmentQueryService;
        this.featureAccessService = featureAccessService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Transactional
    public StudentTaskAssignmentView claim(AuthenticatedUser currentUser, Long assignmentId) {
        ExecutionContext context = lockOwnedAssignment(currentUser, assignmentId);
        requireStatus(context.state(), TaskAssignmentStatus.PENDING_CLAIM);
        transition(context, TaskAssignmentStatus.IN_PROGRESS, TaskAssignmentEventType.CLAIMED,
                null, context.now());
        return assignmentQueryService.findById(currentUser, assignmentId);
    }

    @Transactional
    public StudentTaskAssignmentView pause(
            AuthenticatedUser currentUser, Long assignmentId, PauseTaskCommand command
    ) {
        if (command == null || command.pauseType() == null
                || command.durationMinutes() < 1 || command.durationMinutes() > MAX_PAUSE_MINUTES) {
            throw new IllegalArgumentException("暂停参数不合法");
        }
        ExecutionContext context = lockOwnedAssignment(currentUser, assignmentId);
        requireStatus(context.state(), TaskAssignmentStatus.IN_PROGRESS);
        closeExpiredPause(context.state().id(), context.now());
        if (pauseMapper.findActive(context.state().id(), context.now()) != null) {
            throw new IllegalStateException("任务已暂停");
        }
        TaskPause pause = new TaskPause(
                idGenerator.nextId(), context.state().id(), command.pauseType(),
                currentUser.userId(), context.now(),
                context.now().plusMinutes(command.durationMinutes()), null, null);
        requireSingleWrite(pauseMapper.insert(pause));
        transition(context, TaskAssignmentStatus.IN_PROGRESS, TaskAssignmentEventType.PAUSED,
                command.pauseType().name(), null);
        return assignmentQueryService.findById(currentUser, assignmentId);
    }

    @Transactional
    public StudentTaskAssignmentView resume(AuthenticatedUser currentUser, Long assignmentId) {
        ExecutionContext context = lockOwnedAssignment(currentUser, assignmentId);
        requireStatus(context.state(), TaskAssignmentStatus.IN_PROGRESS);
        closeExpiredPause(context.state().id(), context.now());
        TaskPause activePause = pauseMapper.findActive(context.state().id(), context.now());
        if (activePause == null) {
            throw new IllegalStateException("任务未暂停");
        }
        requireSingleWrite(pauseMapper.resume(activePause.id(), context.now()));
        transition(context, TaskAssignmentStatus.IN_PROGRESS, TaskAssignmentEventType.RESUMED,
                activePause.pauseType().name(), null);
        return assignmentQueryService.findById(currentUser, assignmentId);
    }

    @Transactional
    public StudentTaskAssignmentView abandon(
            AuthenticatedUser currentUser, Long assignmentId, AbandonTaskCommand command
    ) {
        String reason = normalizeOptional(command == null ? null : command.reason(), MAX_REASON_LENGTH);
        ExecutionContext context = lockOwnedAssignment(currentUser, assignmentId);
        requireStatus(context.state(), TaskAssignmentStatus.IN_PROGRESS);
        pauseMapper.terminateActive(context.state().id(), context.now());
        transition(context, TaskAssignmentStatus.NEEDS_IMPROVEMENT,
                TaskAssignmentEventType.ABANDONED, reason, null);
        return assignmentQueryService.findById(currentUser, assignmentId);
    }

    @Transactional
    public StudentTaskAssignmentView submitCheckIn(
            AuthenticatedUser currentUser, Long assignmentId, SubmitTaskCheckInCommand command
    ) {
        String content = normalizeRequired(command == null ? null : command.content(),
                MAX_CHECKIN_CONTENT_LENGTH, "打卡内容");
        ExecutionContext context = lockOwnedAssignment(currentUser, assignmentId);
        requireStatus(context.state(), TaskAssignmentStatus.IN_PROGRESS);
        closeExpiredPause(context.state().id(), context.now());
        if (pauseMapper.findActive(context.state().id(), context.now()) != null) {
            throw new IllegalStateException("暂停期间不能提交打卡");
        }
        if (context.state().currentReviewerId() == null) {
            throw new IllegalStateException("任务没有有效审核人");
        }
        TaskCheckIn checkIn = new TaskCheckIn(
                idGenerator.nextId(), context.state().id(),
                checkInMapper.nextSubmissionNo(context.state().id()), content, "SUBMITTED",
                currentUser.userId(), context.now(), null, null, null);
        requireSingleWrite(checkInMapper.insert(checkIn));
        transition(context, TaskAssignmentStatus.PENDING_REVIEW,
                TaskAssignmentEventType.CHECKED_IN, null, null);
        return assignmentQueryService.findById(currentUser, assignmentId);
    }

    private ExecutionContext lockOwnedAssignment(AuthenticatedUser currentUser, Long assignmentId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        if (assignmentId == null || assignmentId <= 0) {
            throw new IllegalArgumentException("任务实例标识不合法");
        }
        Student student = currentStudentAccessService.require(currentUser);
        TaskAssignmentStateRow state = assignmentMapper.findStateByIdAndStudentIdForUpdate(
                assignmentId, student.id());
        if (state == null) {
            throw new ResourceNotFoundException("学生任务不存在或不可访问");
        }
        return new ExecutionContext(state, LocalDateTime.now(clock), currentUser);
    }

    private void transition(
            ExecutionContext context,
            TaskAssignmentStatus nextStatus,
            TaskAssignmentEventType eventType,
            String reason,
            LocalDateTime claimedAt
    ) {
        TaskAssignmentStatus previousStatus = context.state().currentStatus();
        int updated = assignmentMapper.transitionStatus(
                context.state().id(), previousStatus.name(), nextStatus.name(),
                context.state().versionNo(), context.now(), claimedAt);
        requireSingleWrite(updated);
        TaskAssignmentEvent event = new TaskAssignmentEvent(
                idGenerator.nextId(), context.state().id(), eventType,
                context.currentUser().userId(), previousStatus, nextStatus,
                reason, null, context.now());
        requireSingleWrite(eventMapper.insert(event));
    }

    private void closeExpiredPause(Long assignmentId, LocalDateTime now) {
        pauseMapper.closeExpired(assignmentId, now);
    }

    private void requireStatus(TaskAssignmentStateRow state, TaskAssignmentStatus expected) {
        if (state.currentStatus() != expected) {
            throw new IllegalStateException("任务状态不允许执行此操作");
        }
    }

    private void requireSingleWrite(int affectedRows) {
        if (affectedRows != 1) {
            throw new IllegalStateException("任务状态已变化");
        }
    }

    private String normalizeRequired(String value, int maximumLength, String fieldName) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(fieldName + "不合法");
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maximumLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException("原因不合法");
        }
        return normalized;
    }

    private record ExecutionContext(
            TaskAssignmentStateRow state,
            LocalDateTime now,
            AuthenticatedUser currentUser
    ) {
    }
}
