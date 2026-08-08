package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.application.AuthenticatedSession;
import com.lingdong.learning.auth.application.StudentQrAuthenticatedSession;

import java.time.LocalDateTime;

/** 小程序扫码登录成功响应。 */
public record StudentQrSessionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long sessionId,
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        String studentAccount
) {
    public static StudentQrSessionResponse from(StudentQrAuthenticatedSession result) {
        AuthenticatedSession session = result.session();
        return new StudentQrSessionResponse(
                session.sessionId(), session.accessToken(), session.refreshToken(),
                session.accessExpiresAt(), session.refreshExpiresAt(), result.studentAccount());
    }
}
