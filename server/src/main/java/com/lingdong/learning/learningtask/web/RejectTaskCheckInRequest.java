package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.RejectTaskCheckInCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 审核驳回请求。 */
public record RejectTaskCheckInRequest(
        @NotBlank @Size(max = 500) String reviewComment
) {
    RejectTaskCheckInCommand toCommand() {
        return new RejectTaskCheckInCommand(reviewComment);
    }
}
