package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

/** 班级停用时锁定的未完成机构任务最小状态。 */
public record ClassTaskAssignmentStateRow(
        Long assignmentId,
        TaskAssignmentStatus currentStatus,
        Integer versionNo
) {
}
