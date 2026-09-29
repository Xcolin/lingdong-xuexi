package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.IssuedParentSmsCode;

import java.time.Instant;

/** 验证码发送元数据，响应中不包含验证码。 */
public record ParentSmsCodeResponse(Instant expiresAt, long retryAfterSeconds) {
    public static ParentSmsCodeResponse from(IssuedParentSmsCode issued) {
        return new ParentSmsCodeResponse(issued.expiresAt(), issued.retryAfterSeconds());
    }
}
