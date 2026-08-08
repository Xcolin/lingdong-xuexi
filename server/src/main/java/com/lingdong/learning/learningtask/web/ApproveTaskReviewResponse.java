package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ApproveTaskReviewResult;

/** 审核通过与积分入账响应。 */
public record ApproveTaskReviewResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        String currentStatus,
        @JsonSerialize(using = ToStringSerializer.class) Long checkInId,
        String checkInStatus,
        long awardedPoints,
        long totalPoints,
        long availablePoints,
        @JsonSerialize(using = ToStringSerializer.class) Long ledgerId
) {
    static ApproveTaskReviewResponse from(ApproveTaskReviewResult result) {
        return new ApproveTaskReviewResponse(
                result.assignmentId(), result.currentStatus(), result.checkInId(),
                result.checkInStatus(), result.awardedPoints(), result.totalPoints(),
                result.availablePoints(), result.ledgerId());
    }
}
