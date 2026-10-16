package com.lingdong.learning.student.infrastructure.persistence;

/** 机构管理员可见的活动学员关系摘要查询行。 */
public record StudentOrganizationSummaryRow(
        Long studentId,
        String studentName,
        String gradeCode,
        Long enrollmentOrganizationId,
        String enrollmentOrganizationName,
        Long currentClassOrganizationId,
        String currentClassOrganizationName,
        String studentStatus,
        String studentNumber) {
    public StudentOrganizationSummaryRow(
        Long studentId,
        String studentName,
        String gradeCode,
        Long enrollmentOrganizationId,
        String enrollmentOrganizationName,
        Long currentClassOrganizationId,
        String currentClassOrganizationName,
        String studentStatus
){
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,studentStatus,null);
    }

    public StudentOrganizationSummaryRow(Long studentId,String studentName,String gradeCode,Long enrollmentOrganizationId,String enrollmentOrganizationName,Long currentClassOrganizationId,String currentClassOrganizationName) {
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,null);
    }
}
