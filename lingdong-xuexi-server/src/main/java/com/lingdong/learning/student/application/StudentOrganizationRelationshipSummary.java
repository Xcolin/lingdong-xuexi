package com.lingdong.learning.student.application;

/** 机构管理员可见的活动学员关系摘要。 */
public record StudentOrganizationRelationshipSummary(
        Long studentId,
        String studentName,
        String gradeCode,
        Long enrollmentOrganizationId,
        String enrollmentOrganizationName,
        Long currentClassOrganizationId,
        String currentClassOrganizationName
) {
}
