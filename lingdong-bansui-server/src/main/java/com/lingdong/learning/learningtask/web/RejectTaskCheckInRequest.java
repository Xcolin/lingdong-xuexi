package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.RejectTaskCheckInCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/** 审核驳回请求。 */
public record RejectTaskCheckInRequest(
        @NotBlank @Size(max = 500) String reviewComment,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{18}")
        @JsonDeserialize(using = ReviewCheckInIdDeserializer.class) String expectedCheckInId
) {
    RejectTaskCheckInCommand toCommand() {
        return new RejectTaskCheckInCommand(reviewComment, Long.valueOf(expectedCheckInId));
    }
}
