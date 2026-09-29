package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;

import java.time.LocalDateTime;

/** 固定任务计划停止结果及审计信息。 */
public record StopRecurringTaskResult(
        Long taskId,
        Long recurrenceId,
        LearningTaskRecurrenceStatus status,
        Long stoppedByUserId,
        LocalDateTime stoppedAt
) {
}
