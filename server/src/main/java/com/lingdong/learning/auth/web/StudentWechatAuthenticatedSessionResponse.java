package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.application.StudentWechatAuthenticatedSession;

import java.time.LocalDateTime;

/** 学生微信认证成功响应。 */
public record StudentWechatAuthenticatedSessionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long sessionId,
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        String studentAccount
) {
    static StudentWechatAuthenticatedSessionResponse from(StudentWechatAuthenticatedSession authenticated) {
        var session = authenticated.session();
        return new StudentWechatAuthenticatedSessionResponse(
                session.sessionId(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.refreshExpiresAt(), authenticated.studentAccount());
    }
}
