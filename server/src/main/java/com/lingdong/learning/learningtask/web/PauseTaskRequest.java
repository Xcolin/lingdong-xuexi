package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.PauseTaskCommand;
import com.lingdong.learning.learningtask.domain.TaskPauseType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 学生暂停任务请求。 */
public record PauseTaskRequest(
        @NotNull TaskPauseType pauseType,
        @NotNull @Min(1) @Max(120) Integer durationMinutes
) {
    PauseTaskCommand toCommand() {
        return new PauseTaskCommand(pauseType, durationMinutes);
    }
}
