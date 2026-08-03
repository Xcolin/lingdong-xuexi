package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/** 登录或刷新成功时返回的会话凭证响应。 */
public record SessionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long sessionId,
        String accessToken,
        String refreshToken,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt
) { }
