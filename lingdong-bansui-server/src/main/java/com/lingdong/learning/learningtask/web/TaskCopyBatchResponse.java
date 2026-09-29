package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.TaskCopyBatchResult;

import java.time.LocalDate;
import java.util.List;

/** 复制昨日任务批次响应。 */
public record TaskCopyBatchResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long batchId,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        LocalDate sourceDate,
        LocalDate targetDate,
        String status,
        int totalCount,
        int successCount,
        int failureCount,
        List<TaskCopyItemResponse> items
) {
    static TaskCopyBatchResponse from(TaskCopyBatchResult result) {
        return new TaskCopyBatchResponse(
                result.batchId(), result.studentId(), result.sourceDate(), result.targetDate(),
                result.status(), result.totalCount(), result.successCount(), result.failureCount(),
                result.items().stream().map(TaskCopyItemResponse::from).toList());
    }
}
