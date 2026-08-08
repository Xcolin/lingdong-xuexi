package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

/** 当前审核人可见的待审核任务视图。 */
public record TaskReviewView(
        Long assignmentId,
        Long taskId,
        String title,
        Integer basePoints,
        Long studentId,
        String studentName,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String sourceOrganizationName,
        String currentStatus,
        Long currentReviewerId,
        String reviewerDisplayName,
        TaskCheckInView latestCheckIn
) {
}
