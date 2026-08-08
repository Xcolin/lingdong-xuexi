package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskCopyItemResult;

/** 单个昨日任务复制结果响应。 */
public record TaskCopyItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long itemId,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceTaskId,
        @JsonSerialize(using = ToStringSerializer.class) Long targetTaskId,
        String taskTitle,
        String status,
        String failureCode,
        String failureMessage,
        int retryCount
) {
    static TaskCopyItemResponse from(TaskCopyItemResult result) {
        return new TaskCopyItemResponse(
                result.itemId(), result.sourceTaskId(), result.targetTaskId(),
                result.taskTitle(), result.status(), result.failureCode(),
                result.failureMessage(), result.retryCount());
    }
}
