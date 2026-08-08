package com.lingdong.learning.learningtask.infrastructure.persistence;

import java.time.LocalDateTime;

/** 复制昨日任务条目持久化视图。 */
public record TaskCopyItemRow(
        Long id,
        Long batchId,
        Long sourceTaskId,
        Long targetTaskId,
        String taskTitleSnapshot,
        String status,
        String failureCode,
        String failureMessage,
        Integer retryCount,
        LocalDateTime lastAttemptAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
