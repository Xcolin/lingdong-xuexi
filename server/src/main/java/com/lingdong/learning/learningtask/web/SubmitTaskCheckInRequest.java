package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.SubmitTaskCheckInCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 学生文字和图片打卡请求。 */
public record SubmitTaskCheckInRequest(
        @Size(max = 1000) String content,
        @Valid @Size(max = 9) List<@Positive Long> fileIds
) {
    SubmitTaskCheckInCommand toCommand() {
        return new SubmitTaskCheckInCommand(content, fileIds == null ? List.of() : List.copyOf(fileIds));
    }
}
