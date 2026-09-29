package com.lingdong.learning.student.application;

/** 初始化或重置成功的一次性学生登录凭证响应数据。 */
public record StudentCredentialIssueResult(String studentAccount, String plainLoginCode) {
}
