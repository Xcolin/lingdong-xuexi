package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学生任务进度持久化投影。 */
public record ManagedTaskProgressRow(
        Long assignmentId,
        Long studentId,
        String studentName,
        String studentAccount,
        Long classOrganizationId,
        String className,
        TaskAssignmentStatus currentStatus,
        LocalDate scheduledDate,
        LocalDateTime claimedAt,
        LocalDateTime completedAt,
        LocalDateTime lastTransitionAt
) {
}
