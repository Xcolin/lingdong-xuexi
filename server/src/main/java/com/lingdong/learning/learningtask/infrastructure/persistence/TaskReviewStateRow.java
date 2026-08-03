package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

/** 审核写操作锁定后使用的任务实例快照。 */
public record TaskReviewStateRow(
        Long assignmentId,
        Long studentId,
        TaskAssignmentStatus currentStatus,
        Long currentReviewerId,
        Integer versionNo
) {
}
