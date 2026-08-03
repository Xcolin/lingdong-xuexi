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
        LearningTaskStatus status,
        LocalDateTime publishedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
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
                draft.categoryCode(), draft.remark(), reviewerUserId, 72, LearningTaskStatus.DRAFT,
                null, null, null);
    }

    public LearningTask withDraft(ValidatedLearningTaskDraft draft, Long reviewerUserId) {
        return new LearningTask(
                id, sourceType, sourceOrganizationId, creatorUserId, draft.title(),
                draft.difficultyLevel(), draft.basePoints(), draft.durationMinutes(), draft.scheduledDate(),
                draft.categoryCode(), draft.remark(), reviewerUserId, reviewTimeoutHours, status,
                publishedAt, createdAt, updatedAt);
    }
}
