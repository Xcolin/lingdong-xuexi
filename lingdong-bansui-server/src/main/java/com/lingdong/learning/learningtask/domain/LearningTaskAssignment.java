package com.lingdong.learning.learningtask.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 发布时按学生展开的稳定任务实例。 */
public record LearningTaskAssignment(
        Long id,
        Long taskId,
        Long studentId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String currentStatus,
        Long currentReviewerId,
        LocalDate scheduledDate,
        LocalDateTime dueAt
) {
}
