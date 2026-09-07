package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 当前家长关系下可选择的学生。 */
public record ParentRelationshipStudentView(
        Long studentId,
        String studentName,
        ParentRelationshipRole relationshipRole
) {
}
