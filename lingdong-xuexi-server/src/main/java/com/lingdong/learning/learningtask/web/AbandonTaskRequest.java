package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.AbandonTaskCommand;
import jakarta.validation.constraints.Size;

/** 学生放弃任务请求；原因可选且不用于负面评价。 */
public record AbandonTaskRequest(@Size(max = 500) String reason) {
    AbandonTaskCommand toCommand() {
        return new AbandonTaskCommand(reason);
    }
}
