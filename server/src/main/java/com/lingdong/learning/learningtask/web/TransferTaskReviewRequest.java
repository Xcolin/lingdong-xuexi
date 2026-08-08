package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.TransferTaskReviewCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 审核责任转交请求。 */
public record TransferTaskReviewRequest(
        @NotNull @Positive Long reviewerUserId,
        @NotBlank @Size(max = 500) String transferReason
) {
    TransferTaskReviewCommand toCommand() {
        return new TransferTaskReviewCommand(reviewerUserId, transferReason);
    }
}
