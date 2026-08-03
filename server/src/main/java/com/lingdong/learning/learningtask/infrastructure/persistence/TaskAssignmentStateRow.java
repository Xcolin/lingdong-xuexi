package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

/** 写操作锁定后使用的最小任务实例快照。 */
public record TaskAssignmentStateRow(
        Long id,
        Long studentId,
        TaskAssignmentStatus currentStatus,
        Long currentReviewerId,
        Integer versionNo
) {
}
