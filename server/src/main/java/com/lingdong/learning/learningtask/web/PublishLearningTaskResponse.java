package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.PublishLearningTaskResult;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;

/** 单任务发布结果响应。 */
public record PublishLearningTaskResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        int assignmentCount,
        LearningTaskStatus status
) {
    static PublishLearningTaskResponse from(PublishLearningTaskResult result) {
        return new PublishLearningTaskResponse(
                result.taskId(), result.assignmentCount(), result.status());
    }
}
