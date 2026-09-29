package com.lingdong.learning.learningtask.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 已发布每日固定任务的独立调度计划。 */
public record LearningTaskRecurrence(
        Long id,
        Long taskId,
        String frequencyType,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextGenerationDate,
        LearningTaskRecurrenceStatus status,
        Long stoppedByUserId,
        LocalDateTime stoppedAt,
        Integer versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static LearningTaskRecurrence active(
            Long id, Long taskId, LocalDate startDate, LocalDate endDate
    ) {
        return new LearningTaskRecurrence(
                id, taskId, "DAILY", startDate, endDate, startDate.plusDays(1),
                LearningTaskRecurrenceStatus.ACTIVE, null, null, 0, null, null);
    }
}
