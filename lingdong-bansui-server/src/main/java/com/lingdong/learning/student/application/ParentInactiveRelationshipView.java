package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

import java.time.LocalDateTime;

/** 家长视角的已失效历史关系。 */
public record ParentInactiveRelationshipView(
        Long studentId,
        String studentName,
        ParentRelationshipRole relationshipRole,
        LocalDateTime boundAt,
        LocalDateTime unboundAt
) {
}
