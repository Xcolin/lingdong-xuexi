package com.lingdong.learning.common.security;

/** 认证或授权失败时返回的最小错误响应。 */
public record SecurityErrorResponse(String code, String message, String traceId) { }
