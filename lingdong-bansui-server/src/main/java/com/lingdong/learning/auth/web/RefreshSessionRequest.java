package com.lingdong.learning.auth.web;

import jakarta.validation.constraints.NotBlank;

/** 刷新设备会话凭证的 HTTP 请求。 */
public record RefreshSessionRequest(@NotBlank String refreshToken) { }
