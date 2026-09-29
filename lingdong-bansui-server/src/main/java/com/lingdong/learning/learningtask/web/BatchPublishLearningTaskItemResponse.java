package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.BatchPublishLearningTaskItemResult;

/** 批量发布中的单任务响应。 */
public record BatchPublishLearningTaskItemResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        boolean success,
        Integer assignmentCount,
        String failureReason
) {
    static BatchPublishLearningTaskItemResponse from(BatchPublishLearningTaskItemResult result) {
        return new BatchPublishLearningTaskItemResponse(
                result.taskId(), result.success(), result.assignmentCount(), result.failureReason());
    }
}
