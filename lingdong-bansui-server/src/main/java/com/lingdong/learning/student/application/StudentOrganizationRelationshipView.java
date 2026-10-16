package com.lingdong.learning.student.application;
import com.lingdong.learning.student.domain.StudentOrganizationChange;
import java.util.List;
public record StudentOrganizationRelationshipView(Long studentId,Long enrollmentOrganizationId,
        Long currentClassOrganizationId,String status,List<StudentOrganizationChange> changes,List<Long> currentClassOrganizationIds) {
    public StudentOrganizationRelationshipView(Long studentId,Long enrollmentOrganizationId,Long currentClassOrganizationId,
            String status,List<StudentOrganizationChange> changes){
        this(studentId,enrollmentOrganizationId,currentClassOrganizationId,status,changes,currentClassOrganizationId==null?List.of():List.of(currentClassOrganizationId));
    }
}
