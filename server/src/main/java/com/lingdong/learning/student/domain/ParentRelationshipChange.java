package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 家长关系变更的不可变审计事实。 */
public record ParentRelationshipChange(
        Long id,
        Long studentId,
        Long relationshipId,
        Long invitationId,
        ParentRelationshipChangeType operationType,
        Long operatorUserId,
        Long previousParentUserId,
        Long newParentUserId,
        ParentRelationshipRole previousRole,
        ParentRelationshipRole newRole,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {
}
