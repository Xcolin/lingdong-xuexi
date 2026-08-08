package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.StudentTaskAssignmentView;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 小程序端学生本人任务响应。 */
public record StudentTaskAssignmentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        LearningTaskSourceType sourceType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceOrganizationId,
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
        @JsonSerialize(using = ToStringSerializer.class) Long currentReviewerId,
        String reviewerDisplayName,
        TaskDeferType lastDeferType,
        boolean overnightMigrated,
        ActiveTaskPauseResponse activePause,
        TaskCheckInResponse latestCheckIn,
        List<String> tagCodes
) {
    static StudentTaskAssignmentResponse from(StudentTaskAssignmentView view) {
        return new StudentTaskAssignmentResponse(
                view.id(), view.taskId(), view.sourceType(), view.sourceOrganizationId(),
                view.sourceOrganizationName(), view.title(), view.difficultyLevel(),
                view.basePoints(), view.durationMinutes(), view.scheduledDate(), view.dueAt(),
                view.categoryCode(), view.remark(), view.currentStatus(), view.effectiveStatus(),
                view.currentReviewerId(), view.reviewerDisplayName(),
                view.lastDeferType(), view.overnightMigrated(),
                ActiveTaskPauseResponse.from(view.activePause()),
                TaskCheckInResponse.from(view.latestCheckIn()), view.tagCodes());
    }
}
