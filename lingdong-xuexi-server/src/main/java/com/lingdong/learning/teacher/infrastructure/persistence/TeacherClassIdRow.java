package com.lingdong.learning.teacher.infrastructure.persistence;

/** 本页教师与活动班级标识的批量查询结果。 */
public record TeacherClassIdRow(Long teacherUserId, Long classOrganizationId) {
}
