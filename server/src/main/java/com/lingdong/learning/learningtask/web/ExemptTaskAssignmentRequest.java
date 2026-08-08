package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.ExemptTaskAssignmentCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 设置任务免执行请求。 */
public record ExemptTaskAssignmentRequest(
        @NotBlank @Size(max = 500) String reason
) {
    ExemptTaskAssignmentCommand toCommand() {
        return new ExemptTaskAssignmentCommand(reason);
    }
}
