package com.lingdong.learning.learningtask.domain;

import com.lingdong.learning.learningtask.application.ValidatedLearningTaskDraft;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 可编辑草稿和不可变已发布任务共享的定义。 */
public record LearningTask(
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
        String remark,
        Long reviewerUserId,
        Integer reviewTimeoutHours,
        Boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LearningTaskRecurrenceStatus recurrenceStatus,
        String recurrenceFrequency, java.util.List<Integer> recurrenceWeekdays,
        String description, java.util.List<String> requirementItems, java.util.List<Long> exampleFileIds,
        String taskType, LocalDate endDate, String visibility
) {
    public LearningTask {
        requirementItems = requirementItems == null ? java.util.List.of() : java.util.List.copyOf(requirementItems);
        exampleFileIds = exampleFileIds == null ? java.util.List.of() : java.util.List.copyOf(exampleFileIds);
    }

    public LearningTask(
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
        String remark,
        Long reviewerUserId,
        Integer reviewTimeoutHours,
        Boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LearningTaskRecurrenceStatus recurrenceStatus
    ) { this(id, sourceType, sourceOrganizationId, creatorUserId, title, difficultyLevel, basePoints, durationMinutes, scheduledDate, categoryCode, remark, reviewerUserId, reviewTimeoutHours, recurrenceEnabled, recurrenceEndDate, status, publishedAt, createdAt, updatedAt, recurrenceStatus, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null); }

    public static LearningTask draft(
            Long id,
            LearningTaskSourceType sourceType,
            Long sourceOrganizationId,
            Long creatorUserId,
            Long reviewerUserId,
            ValidatedLearningTaskDraft draft
    ) {
        return new LearningTask(
                id, sourceType, sourceOrganizationId, creatorUserId, draft.title(),
                draft.difficultyLevel(), draft.basePoints(), draft.durationMinutes(), draft.scheduledDate(),
                draft.categoryCode(), draft.remark(), reviewerUserId, 72,
                draft.recurrenceEnabled(), draft.recurrenceEndDate(), LearningTaskStatus.DRAFT,
                null, null, null, null, draft.recurrenceFrequency(), draft.recurrenceWeekdays(),
                draft.description(), draft.requirementItems(), draft.exampleFileIds(),
                draft.taskType(), draft.endDate(), draft.visibility());
    }

    public LearningTask withDraft(ValidatedLearningTaskDraft draft, Long reviewerUserId) {
        return new LearningTask(
                id, sourceType, sourceOrganizationId, creatorUserId, draft.title(),
                draft.difficultyLevel(), draft.basePoints(), draft.durationMinutes(), draft.scheduledDate(),
                draft.categoryCode(), draft.remark(), reviewerUserId, reviewTimeoutHours,
                draft.recurrenceEnabled(), draft.recurrenceEndDate(), status,
                publishedAt, createdAt, updatedAt, recurrenceStatus, draft.recurrenceFrequency(), draft.recurrenceWeekdays(),
                draft.description(), draft.requirementItems(), draft.exampleFileIds(),
                draft.taskType(), draft.endDate(), draft.visibility());
    }
}
