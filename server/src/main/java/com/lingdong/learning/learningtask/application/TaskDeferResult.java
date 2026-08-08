package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;

/** 顺延操作结果。 */
public record TaskDeferResult(
        Long assignmentId,
        Long targetTaskId,
        String status,
        LocalDate targetDate,
        TaskDeferType deferType,
        boolean overnightMigrated
) {
}
