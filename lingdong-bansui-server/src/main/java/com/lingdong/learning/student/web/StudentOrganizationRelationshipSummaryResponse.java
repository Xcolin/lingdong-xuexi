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
        String currentClassOrganizationName,
        String institutionTypeCode,
        java.util.List<String> currentClassOrganizationIds,
        java.util.List<String> currentClassOrganizationNames,
        String studentStatus,
        String studentNumber) {
    public StudentOrganizationRelationshipSummaryResponse(
        String studentId,
        String studentName,
        String gradeCode,
        String enrollmentOrganizationId,
        String enrollmentOrganizationName,
        String currentClassOrganizationId,
        String currentClassOrganizationName,
        String institutionTypeCode,
        java.util.List<String> currentClassOrganizationIds,
        java.util.List<String> currentClassOrganizationNames,
        String studentStatus
){
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,institutionTypeCode,currentClassOrganizationIds,currentClassOrganizationNames,studentStatus,null);
    }

    public StudentOrganizationRelationshipSummaryResponse(String studentId,String studentName,String gradeCode,String enrollmentOrganizationId,String enrollmentOrganizationName,String currentClassOrganizationId,String currentClassOrganizationName,String institutionTypeCode,java.util.List<String> currentClassOrganizationIds,java.util.List<String> currentClassOrganizationNames) {
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,institutionTypeCode,currentClassOrganizationIds,currentClassOrganizationNames,null);
    }
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
                summary.currentClassOrganizationName(),summary.institutionTypeCode(),summary.currentClassOrganizationIds().stream().map(Object::toString).toList(),summary.currentClassOrganizationNames(),summary.studentStatus(),summary.studentNumber());
    }
}

