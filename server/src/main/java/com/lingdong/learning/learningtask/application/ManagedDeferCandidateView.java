package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;

/** 管理角色可见的待优化或可改期任务摘要。 */
public record ManagedDeferCandidateView(
        Long assignmentId,
        String title,
        Long studentId,
        String studentName,
        LearningTaskSourceType sourceType,
        String sourceOrganizationName,
        LocalDate scheduledDate,
        TaskAssignmentStatus currentStatus,
        TaskDeferType lastDeferType,
        boolean overnightMigrated
) {
}
