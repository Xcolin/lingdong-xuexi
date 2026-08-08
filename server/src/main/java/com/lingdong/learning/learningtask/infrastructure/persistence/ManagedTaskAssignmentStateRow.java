package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

/** 管理角色设置免执行时锁定的任务实例快照。 */
public record ManagedTaskAssignmentStateRow(
        Long assignmentId,
        Long studentId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        Long creatorUserId,
        TaskAssignmentStatus currentStatus,
        Integer versionNo
) {
}
