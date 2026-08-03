package com.lingdong.learning.student.application;

/** 学生身份首次签发结果，明文登录码只允许随当前事务响应一次。 */
public record IssuedStudentCredential(
        Long studentUserId,
        String studentAccount,
        String plainLoginCode
) {
}
