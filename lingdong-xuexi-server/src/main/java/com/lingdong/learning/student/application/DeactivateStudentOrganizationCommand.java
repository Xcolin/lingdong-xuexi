package com.lingdong.learning.student.application;

/** 学生离校或跨机构转学转出命令。 */
public record DeactivateStudentOrganizationCommand(Long organizationId, String reason) {
}
