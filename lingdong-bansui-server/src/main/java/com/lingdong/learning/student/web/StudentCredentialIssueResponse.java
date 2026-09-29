package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.StudentCredentialIssueResult;

/** 初始化或重置成功响应，登录码明文只在当前响应出现。 */
public record StudentCredentialIssueResponse(String studentAccount, String loginCode) {
    static StudentCredentialIssueResponse from(StudentCredentialIssueResult result) {
        return new StudentCredentialIssueResponse(result.studentAccount(), result.plainLoginCode());
    }
}
