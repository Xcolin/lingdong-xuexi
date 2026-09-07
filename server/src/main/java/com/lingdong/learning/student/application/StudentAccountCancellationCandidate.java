package com.lingdong.learning.student.application;

/** 可由当前机构管理员注销的学生账号摘要。 */
public record StudentAccountCancellationCandidate(
        Long studentId,
        String studentName,
        String studentAccount,
        Long organizationId,
        String organizationName
) {
}

