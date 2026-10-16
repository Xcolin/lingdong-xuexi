package com.lingdong.learning.student.application;
import java.util.List;
public record StudentOrganizationRelationshipSummary(Long studentId,String studentName,String gradeCode,
        Long enrollmentOrganizationId,String enrollmentOrganizationName,Long currentClassOrganizationId,
        String currentClassOrganizationName,String institutionTypeCode,List<Long> currentClassOrganizationIds,
        List<String> currentClassOrganizationNames,String studentStatus,String studentNumber) {
    public StudentOrganizationRelationshipSummary(Long studentId,String studentName,String gradeCode,
        Long enrollmentOrganizationId,String enrollmentOrganizationName,Long currentClassOrganizationId,
        String currentClassOrganizationName,String institutionTypeCode,List<Long> currentClassOrganizationIds,
        List<String> currentClassOrganizationNames,String studentStatus){
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,institutionTypeCode,currentClassOrganizationIds,currentClassOrganizationNames,studentStatus,null);
    }

    public StudentOrganizationRelationshipSummary(Long studentId,String studentName,String gradeCode,Long enrollmentOrganizationId,String enrollmentOrganizationName,Long currentClassOrganizationId,String currentClassOrganizationName,String institutionTypeCode,List<Long> currentClassOrganizationIds,List<String> currentClassOrganizationNames) {
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,currentClassOrganizationName,institutionTypeCode,currentClassOrganizationIds,currentClassOrganizationNames,null);
    }
    public StudentOrganizationRelationshipSummary(Long studentId,String studentName,String gradeCode,
            Long enrollmentOrganizationId,String enrollmentOrganizationName,Long currentClassOrganizationId,String currentClassOrganizationName){
        this(studentId,studentName,gradeCode,enrollmentOrganizationId,enrollmentOrganizationName,currentClassOrganizationId,
            currentClassOrganizationName,null,currentClassOrganizationId==null?List.of():List.of(currentClassOrganizationId),
            currentClassOrganizationName==null?List.of():List.of(currentClassOrganizationName));
    }
}
