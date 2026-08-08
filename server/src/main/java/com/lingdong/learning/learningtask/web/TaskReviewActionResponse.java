package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskReviewActionResult;

/** 审核操作响应。 */
public record TaskReviewActionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        String currentStatus,
        @JsonSerialize(using = ToStringSerializer.class) Long checkInId,
        String checkInStatus
) {
    static TaskReviewActionResponse from(TaskReviewActionResult result) {
        return new TaskReviewActionResponse(
                result.assignmentId(), result.currentStatus(),
                result.checkInId(), result.checkInStatus());
    }
}
