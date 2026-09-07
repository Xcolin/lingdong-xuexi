package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentOrganizationRelationshipView;

import java.util.List;

/** 学生当前机构关系和变更历史响应。 */
public record StudentOrganizationRelationshipResponse(
        String studentId,
        String enrollmentOrganizationId,
        String currentClassOrganizationId,
        String status,
        List<StudentOrganizationChangeResponse> changes
) {
    static StudentOrganizationRelationshipResponse from(StudentOrganizationRelationshipView view) {
        return new StudentOrganizationRelationshipResponse(
                view.studentId().toString(),
                view.enrollmentOrganizationId().toString(),
                view.currentClassOrganizationId() == null
                        ? null : view.currentClassOrganizationId().toString(),
                view.status(),
                view.changes().stream().map(StudentOrganizationChangeResponse::from).toList());
    }
}
