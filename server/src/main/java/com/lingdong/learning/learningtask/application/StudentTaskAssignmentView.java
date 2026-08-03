package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 学生本人可见的任务实例视图。 */
public record StudentTaskAssignmentView(
        Long id,
        Long taskId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String sourceOrganizationName,
        String title,
        Integer difficultyLevel,
        Integer basePoints,
        Integer durationMinutes,
        LocalDate scheduledDate,
        LocalDateTime dueAt,
        String categoryCode,
        String remark,
        String currentStatus,
        String effectiveStatus,
        Long currentReviewerId,
        String reviewerDisplayName,
        ActiveTaskPauseView activePause,
        TaskCheckInView latestCheckIn,
        List<String> tagCodes
) {
    public StudentTaskAssignmentView {
        tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
    }

    public StudentTaskAssignmentView withTagCodes(List<String> codes) {
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                activePause, latestCheckIn, codes);
    }
}
