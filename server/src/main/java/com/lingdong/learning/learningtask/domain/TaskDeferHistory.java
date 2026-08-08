package com.lingdong.learning.learningtask.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 不可变的任务顺延历史。 */
public record TaskDeferHistory(
        Long id,
        Long assignmentId,
        Long sourceTaskId,
        Long targetTaskId,
        LocalDate sourceScheduledDate,
        LocalDate targetScheduledDate,
        TaskDeferType deferType,
        Long operatorUserId,
        LocalDateTime occurredAt
) {
}
