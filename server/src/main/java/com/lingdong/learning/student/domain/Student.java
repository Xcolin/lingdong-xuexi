package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 不包含登录凭证的学生基础档案。 */
public record Student(
        Long id,
        String studentName,
        String gradeCode,
        Long studentUserId,
        StudentStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static Student create(Long id, String studentName, String gradeCode, Long studentUserId) {
        return new Student(id, studentName, gradeCode, studentUserId, StudentStatus.ENABLED, null, null);
    }
}
