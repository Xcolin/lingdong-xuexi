package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 管理者可见的脱敏学生任务进度。 */
public record ManagedTaskProgressView(
        Long assignmentId,
        Long studentId,
        String studentName,
        String studentAccountMasked,
        Long classOrganizationId,
        String className,
        TaskAssignmentStatus currentStatus,
        LocalDate scheduledDate,
        LocalDateTime claimedAt,
        LocalDateTime completedAt,
        LocalDateTime lastTransitionAt
) {
}
