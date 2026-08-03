package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.SubmitTaskCheckInCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 学生文字打卡请求。 */
public record SubmitTaskCheckInRequest(
        @NotBlank @Size(max = 1000) String content
) {
    SubmitTaskCheckInCommand toCommand() {
        return new SubmitTaskCheckInCommand(content);
    }
}
