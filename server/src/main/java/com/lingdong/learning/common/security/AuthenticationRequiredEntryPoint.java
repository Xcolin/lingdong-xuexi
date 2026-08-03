package com.lingdong.learning.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 将匿名或失效会话请求统一转换为不泄露内部状态的 401 响应。 */
@Component
public class AuthenticationRequiredEntryPoint implements AuthenticationEntryPoint {
    private final SecurityErrorResponseWriter responseWriter;

    public AuthenticationRequiredEntryPoint(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        responseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "AUTH_REQUIRED", "认证失败");
    }
}
