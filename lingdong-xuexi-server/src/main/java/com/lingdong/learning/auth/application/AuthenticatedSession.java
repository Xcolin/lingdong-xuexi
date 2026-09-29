package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 仅在登录或刷新成功时返回给调用方的原始会话凭证。 */
public record AuthenticatedSession(
        Long sessionId,
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt
) { }
