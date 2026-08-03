package com.lingdong.learning.student.application;

/** 学生当前活动班级的写入结果。 */
public record StudentClassAssignment(Long studentId, Long classOrganizationId, String status) {
}
