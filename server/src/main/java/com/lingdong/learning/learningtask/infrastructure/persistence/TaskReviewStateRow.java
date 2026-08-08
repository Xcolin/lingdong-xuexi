package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDate;

/** 审核写操作锁定后使用的任务实例快照。 */
public record TaskReviewStateRow(
        Long assignmentId,
        Long taskId,
        Long studentId,
        Integer basePoints,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        LocalDate scheduledDate,
        TaskAssignmentStatus currentStatus,
        Long currentReviewerId,
        Integer versionNo
) {
}
