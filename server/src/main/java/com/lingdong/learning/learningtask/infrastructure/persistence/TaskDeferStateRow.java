package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;
import com.lingdong.learning.learningtask.domain.TaskDeferType;

import java.time.LocalDate;

/** 顺延操作锁定后使用的任务实例与来源快照。 */
public record TaskDeferStateRow(
        Long assignmentId,
        Long taskId,
        Long studentId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        Long creatorUserId,
        TaskAssignmentStatus currentStatus,
        Integer versionNo,
        LocalDate scheduledDate,
        TaskDeferType lastDeferType,
        Integer deferCount
) {
}
