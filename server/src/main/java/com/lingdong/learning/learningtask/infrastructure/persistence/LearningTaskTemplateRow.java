package com.lingdong.learning.learningtask.infrastructure.persistence;

import java.time.LocalDateTime;

/** 任务模板主表的持久化行。 */
public record LearningTaskTemplateRow(
        Long id,
        String templateScope,
        Long ownerUserId,
        String ownerScopeKey,
        String templateName,
        String activeNameKey,
        String taskTitle,
        Integer difficultyLevel,
        Integer durationMinutes,
        String categoryCode,
        String remark,
        Integer sortOrder,
        String status,
        Long versionNo,
        Long createdByUserId,
        Long updatedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
