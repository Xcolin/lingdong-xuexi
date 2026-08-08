package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.StopRecurringTaskResult;
import com.lingdong.learning.learningtask.domain.LearningTaskRecurrenceStatus;

import java.time.LocalDateTime;

/** 固定任务计划停止响应，所有雪花标识按字符串输出。 */
public record StopRecurringTaskResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        @JsonSerialize(using = ToStringSerializer.class) Long recurrenceId,
        LearningTaskRecurrenceStatus status,
        @JsonSerialize(using = ToStringSerializer.class) Long stoppedByUserId,
        LocalDateTime stoppedAt
) {
    static StopRecurringTaskResponse from(StopRecurringTaskResult result) {
        return new StopRecurringTaskResponse(
                result.taskId(), result.recurrenceId(), result.status(),
                result.stoppedByUserId(), result.stoppedAt());
    }
}
