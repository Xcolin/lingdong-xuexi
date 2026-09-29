package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskDeferResult;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;

/** 顺延结果响应，所有雪花标识按字符串输出。 */
public record TaskDeferResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long targetTaskId,
        String status,
        LocalDate targetDate,
        TaskDeferType deferType,
        boolean overnightMigrated
) {
    static TaskDeferResponse from(TaskDeferResult result) {
        return new TaskDeferResponse(
                result.assignmentId(), result.targetTaskId(), result.status(), result.targetDate(),
                result.deferType(), result.overnightMigrated());
    }
}
