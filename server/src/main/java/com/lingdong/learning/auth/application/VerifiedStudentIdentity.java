package com.lingdong.learning.auth.application;

/** 已通过学生登录码风控校验但尚未创建会话的学生身份。 */
public record VerifiedStudentIdentity(
        Long studentId,
        Long studentUserId,
        String studentAccount
) {
}
