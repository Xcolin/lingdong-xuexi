package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDateTime;

/** 当前审核人的待审核任务联表查询行。 */
public record TaskReviewRow(
        Long assignmentId,
        Long taskId,
        String title,
        Long studentId,
        String studentName,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String sourceOrganizationName,
        String currentStatus,
        Long currentReviewerId,
        String reviewerDisplayName,
        Long checkInId,
        Integer submissionNo,
        String checkInContent,
        String checkInStatus,
        LocalDateTime submittedAt,
        String reviewComment
) {
}
