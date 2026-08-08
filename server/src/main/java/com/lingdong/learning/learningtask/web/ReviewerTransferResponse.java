package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.ReviewerTransferResult;

/** 审核责任转交响应。 */
public record ReviewerTransferResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long currentReviewerId
) {
    static ReviewerTransferResponse from(ReviewerTransferResult result) {
        return new ReviewerTransferResponse(result.assignmentId(), result.currentReviewerId());
    }
}
