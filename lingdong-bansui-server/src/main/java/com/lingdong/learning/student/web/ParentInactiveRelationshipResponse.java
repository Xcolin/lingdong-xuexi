package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentInactiveRelationshipView;

/** 家长已失效历史关系响应。 */
public record ParentInactiveRelationshipResponse(
        String studentId,
        String studentName,
        String relationshipRole,
        String boundAt,
        String unboundAt
) {
    static ParentInactiveRelationshipResponse from(ParentInactiveRelationshipView view) {
        return new ParentInactiveRelationshipResponse(
                view.studentId().toString(),
                view.studentName(),
                view.relationshipRole().name(),
                view.boundAt() == null ? null : view.boundAt().toLocalDate().toString(),
                view.unboundAt() == null ? null : view.unboundAt().toLocalDate().toString());
    }
}
