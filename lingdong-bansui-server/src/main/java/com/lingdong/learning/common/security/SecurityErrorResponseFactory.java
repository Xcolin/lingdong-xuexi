package com.lingdong.learning.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 统一生成不含敏感细节的安全错误响应及请求追踪标识。 */
@Component
public class SecurityErrorResponseFactory {
    public SecurityErrorResponse create(HttpServletRequest request, String code, String message) {
        String traceId = request.getHeader("X-Request-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        return new SecurityErrorResponse(code, message, traceId);
    }
}
