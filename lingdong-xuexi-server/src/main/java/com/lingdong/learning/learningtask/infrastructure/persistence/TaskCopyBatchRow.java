package com.lingdong.learning.learningtask.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 复制昨日任务批次持久化视图。 */
public record TaskCopyBatchRow(
        Long id,
        Long studentId,
        LocalDate sourceDate,
        LocalDate targetDate,
        Long createdByUserId,
        Boolean confirmDuplicateTitles,
        String status,
        Integer totalCount,
        Integer successCount,
        Integer failureCount,
        LocalDateTime createdAt,
        LocalDateTime completedAt
) {
}
