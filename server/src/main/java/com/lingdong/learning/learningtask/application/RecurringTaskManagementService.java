package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrence;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskMapper;
import com.lingdong.learning.learningtask.infrastructure.persistence.LearningTaskRecurrenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 管理已发布固定任务计划的主动停止与审计。 */
@Service
public class RecurringTaskManagementService {
    private static final String FEATURE_CODE = "LEARNING_TASK_MANAGEMENT";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final LearningTaskMapper taskMapper;
    private final LearningTaskRecurrenceMapper recurrenceMapper;
    private final LearningTaskScopeService scopeService;
    private final FeatureAccessService featureAccessService;
    private final Clock clock;

    public RecurringTaskManagementService(
            LearningTaskMapper taskMapper,
            LearningTaskRecurrenceMapper recurrenceMapper,
            LearningTaskScopeService scopeService,
            FeatureAccessService featureAccessService,
            Clock clock
    ) {
        this.taskMapper = taskMapper;
        this.recurrenceMapper = recurrenceMapper;
        this.scopeService = scopeService;
        this.featureAccessService = featureAccessService;
        this.clock = clock;
    }

    @Transactional
    public StopRecurringTaskResult stop(AuthenticatedUser currentUser, Long taskId) {
        featureAccessService.requireEnabled(FEATURE_CODE, null);
        LearningTask task = taskMapper.findById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("任务不存在或不可访问");
        }
        scopeService.requireManageable(currentUser, task);
        if (task.status() != LearningTaskStatus.PUBLISHED
                || !Boolean.TRUE.equals(task.recurrenceEnabled())) {
            throw new IllegalStateException("当前任务不是运行中的固定任务");
        }

        LearningTaskRecurrence recurrence = recurrenceMapper.findByTaskIdForUpdate(taskId);
        if (recurrence == null || recurrence.status() != LearningTaskRecurrenceStatus.ACTIVE) {
            throw new IllegalStateException("固定任务计划已停止或已完成");
        }
        LocalDateTime stoppedAt = LocalDateTime.now(clock.withZone(BUSINESS_ZONE));
        if (recurrenceMapper.stop(
                recurrence.id(), currentUser.userId(), stoppedAt, recurrence.versionNo()) != 1) {
            throw new IllegalStateException("固定任务计划状态已变化，请重试");
        }
        return new StopRecurringTaskResult(
                task.id(), recurrence.id(), LearningTaskRecurrenceStatus.STOPPED,
                currentUser.userId(), stoppedAt);
    }
}
