package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 教师候选与单个活动班级的数据库行。 */
public record TeacherOptionRow(Long userId, String displayName, Long classOrganizationId) {
}
