package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 教师与班级之间可停用、可恢复的授权关系。 */
public record TeacherClassRelation(
        Long id,
        Long teacherUserId,
        Long classOrganizationId,
        TeacherClassStatus status,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TeacherClassRelation active(Long id, Long teacherUserId, Long classOrganizationId) {
        return new TeacherClassRelation(
                id, teacherUserId, classOrganizationId, TeacherClassStatus.ACTIVE,
                null, null, null, null);
    }
}
