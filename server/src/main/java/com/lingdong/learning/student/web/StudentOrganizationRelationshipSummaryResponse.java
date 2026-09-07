package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentOrganizationRelationshipSummary;

/** 机构管理员学员关系列表项，所有标识按字符串输出。 */
public record StudentOrganizationRelationshipSummaryResponse(
        String studentId,
        String studentName,
        String gradeCode,
        String enrollmentOrganizationId,
        String enrollmentOrganizationName,
        String currentClassOrganizationId,
        String currentClassOrganizationName
) {
    static StudentOrganizationRelationshipSummaryResponse from(
            StudentOrganizationRelationshipSummary summary
    ) {
        return new StudentOrganizationRelationshipSummaryResponse(
                summary.studentId().toString(),
                summary.studentName(),
                summary.gradeCode(),
                summary.enrollmentOrganizationId().toString(),
                summary.enrollmentOrganizationName(),
                summary.currentClassOrganizationId() == null
                        ? null : summary.currentClassOrganizationId().toString(),
                summary.currentClassOrganizationName());
    }
}
