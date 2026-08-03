package com.lingdong.learning.student.application;

/** 创建学生档案时由调用方显式提交的业务参数。 */
public record CreateStudentCommand(String studentName, String gradeCode, Long organizationId) {
}
