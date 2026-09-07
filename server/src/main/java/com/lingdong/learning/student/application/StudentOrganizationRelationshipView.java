package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.StudentOrganizationChange;

import java.util.List;

/** 学生当前机构关系和不可变变更历史。 */
public record StudentOrganizationRelationshipView(
        Long studentId,
        Long enrollmentOrganizationId,
        Long currentClassOrganizationId,
        String status,
        List<StudentOrganizationChange> changes
) {
}
