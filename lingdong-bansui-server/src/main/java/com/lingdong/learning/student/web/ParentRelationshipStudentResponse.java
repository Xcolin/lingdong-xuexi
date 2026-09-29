package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentRelationshipStudentView;
import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 家长关系管理中的学生选择响应。 */
public record ParentRelationshipStudentResponse(
        String studentId,
        String studentName,
        ParentRelationshipRole relationshipRole
) {
    static ParentRelationshipStudentResponse from(ParentRelationshipStudentView view) {
        return new ParentRelationshipStudentResponse(
                view.studentId().toString(), view.studentName(), view.relationshipRole());
    }
}
