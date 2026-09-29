package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 可管理任务列表的轻量响应。 */
public record LearningTaskSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        LearningTaskSourceType sourceType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceOrganizationId,
        String title,
        Integer difficultyLevel,
        Integer basePoints,
        Integer durationMinutes,
        LocalDate scheduledDate,
        boolean recurrenceEnabled,
        LearningTaskRecurrenceStatus recurrenceStatus,
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
    static LearningTaskSummaryResponse from(LearningTask task) {
        return new LearningTaskSummaryResponse(
                task.id(), task.sourceType(), task.sourceOrganizationId(), task.title(),
                task.difficultyLevel(), task.basePoints(), task.durationMinutes(),
                task.scheduledDate(), Boolean.TRUE.equals(task.recurrenceEnabled()),
                task.recurrenceStatus(), task.status(), task.publishedAt(), task.createdAt());
    }
}
