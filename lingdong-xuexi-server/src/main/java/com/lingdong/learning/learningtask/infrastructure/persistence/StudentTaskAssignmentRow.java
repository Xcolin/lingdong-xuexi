package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.application.StudentTaskAssignmentView;
import com.lingdong.learning.learningtask.application.ActiveTaskPauseView;
import com.lingdong.learning.learningtask.application.TaskCheckInView;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskPauseType;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 学生任务实例联表查询行。 */
public record StudentTaskAssignmentRow(
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
        Long currentReviewerId,
        String reviewerDisplayName,
        String effectiveStatus,
        TaskDeferType lastDeferType,
        Boolean overnightMigrated,
        Long activePauseId,
        TaskPauseType activePauseType,
        LocalDateTime pauseStartedAt,
        LocalDateTime pauseExpiresAt,
        Long latestCheckInId,
        Integer latestSubmissionNo,
        String latestCheckInContent,
        String latestCheckInStatus,
        LocalDateTime latestSubmittedAt,
        String latestReviewComment
) {
    public StudentTaskAssignmentView toView(List<String> tagCodes) {
        ActiveTaskPauseView activePause = activePauseId == null ? null : new ActiveTaskPauseView(
                activePauseId, activePauseType, pauseStartedAt, pauseExpiresAt);
        TaskCheckInView latestCheckIn = latestCheckInId == null ? null : new TaskCheckInView(
                latestCheckInId, latestSubmissionNo, latestCheckInContent, latestCheckInStatus,
                latestSubmittedAt, latestReviewComment);
        return new StudentTaskAssignmentView(
                id, taskId, sourceType, sourceOrganizationId, sourceOrganizationName, title,
                difficultyLevel, basePoints, durationMinutes, scheduledDate, dueAt, categoryCode, remark,
                currentStatus, effectiveStatus, currentReviewerId, reviewerDisplayName,
                lastDeferType, Boolean.TRUE.equals(overnightMigrated),
                activePause, latestCheckIn, tagCodes);
    }
}
