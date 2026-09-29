package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 任务实例状态和责任变化的审计事件。 */
public record TaskAssignmentEvent(
        Long id,
        Long assignmentId,
        TaskAssignmentEventType eventType,
        Long operatorUserId,
        TaskAssignmentStatus fromStatus,
        TaskAssignmentStatus toStatus,
        String reason,
        String eventDetails,
        LocalDateTime occurredAt
) {
}
