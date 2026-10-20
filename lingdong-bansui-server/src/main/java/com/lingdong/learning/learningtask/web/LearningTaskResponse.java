package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.LearningTaskDetails;
import com.lingdong.learning.learningtask.domain.LearningTask;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 可管理任务详情响应，保留用户提交的原始目标。 */
public record LearningTaskResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        LearningTaskSourceType sourceType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceOrganizationId,
        @JsonSerialize(using = ToStringSerializer.class) Long creatorUserId,
        String title,
        Integer difficultyLevel,
        Integer basePoints,
        Integer durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewerUserId,
        Integer reviewTimeoutHours,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        LearningTaskRecurrenceStatus recurrenceStatus,
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LearningTaskTargetResponse> targets,
        String recurrenceFrequency, java.util.List<Integer> recurrenceWeekdays,
        String description, java.util.List<String> requirementItems,
        @JsonSerialize(contentUsing = ToStringSerializer.class) java.util.List<Long> exampleFileIds,
        String taskType, LocalDate endDate, String visibility
) {
    public LearningTaskResponse(
        Long id,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        Long creatorUserId,
        String title,
        Integer difficultyLevel,
        Integer basePoints,
        Integer durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        Long reviewerUserId,
        Integer reviewTimeoutHours,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        LearningTaskRecurrenceStatus recurrenceStatus,
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<LearningTaskTargetResponse> targets
) { this(id, sourceType, sourceOrganizationId, creatorUserId, title, difficultyLevel, basePoints, durationMinutes, scheduledDate, categoryCode, tagCodes, remark, reviewerUserId, reviewTimeoutHours, recurrenceEnabled, recurrenceEndDate, recurrenceStatus, status, publishedAt, createdAt, updatedAt, targets, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null); }

    static LearningTaskResponse from(LearningTaskDetails details) {
        LearningTask task = details.task();
        return new LearningTaskResponse(
                task.id(), task.sourceType(), task.sourceOrganizationId(), task.creatorUserId(),
                task.title(), task.difficultyLevel(), task.basePoints(), task.durationMinutes(),
                task.scheduledDate(), task.categoryCode(), details.tagCodes(), task.remark(),
                task.reviewerUserId(), task.reviewTimeoutHours(), Boolean.TRUE.equals(task.recurrenceEnabled()),
                task.recurrenceEndDate(), task.recurrenceStatus(), task.status(), task.publishedAt(),
                task.createdAt(), task.updatedAt(), details.targets().stream()
                .map(LearningTaskTargetResponse::from).toList(), task.recurrenceFrequency(), task.recurrenceWeekdays(),
                task.description(), task.requirementItems(), task.exampleFileIds(),
                task.taskType(), task.endDate(), task.visibility());
    }
}
