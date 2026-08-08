package com.lingdong.learning.learningtask.application;

import java.time.LocalDate;
import java.util.List;

/** 复制昨日任务批次汇总。 */
public record TaskCopyBatchResult(
        Long batchId,
        Long studentId,
        LocalDate sourceDate,
        LocalDate targetDate,
        String status,
        int totalCount,
        int successCount,
        int failureCount,
        List<TaskCopyItemResult> items
) {
    public TaskCopyBatchResult {
        items = List.copyOf(items);
    }
}
