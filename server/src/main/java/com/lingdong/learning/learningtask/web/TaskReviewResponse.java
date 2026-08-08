package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskReviewView;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

/** Web 当前审核人待办响应。 */
public record TaskReviewResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        String title,
        Integer basePoints,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        LearningTaskSourceType sourceType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceOrganizationId,
        String sourceOrganizationName,
        String currentStatus,
        @JsonSerialize(using = ToStringSerializer.class) Long currentReviewerId,
        String reviewerDisplayName,
        TaskCheckInResponse latestCheckIn
) {
    static TaskReviewResponse from(TaskReviewView view) {
        return new TaskReviewResponse(
                view.assignmentId(), view.taskId(), view.title(), view.basePoints(), view.studentId(),
                view.studentName(), view.sourceType(), view.sourceOrganizationId(),
                view.sourceOrganizationName(), view.currentStatus(), view.currentReviewerId(),
                view.reviewerDisplayName(), TaskCheckInResponse.from(view.latestCheckIn()));
    }
}
