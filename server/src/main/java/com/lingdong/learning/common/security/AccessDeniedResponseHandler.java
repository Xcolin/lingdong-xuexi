package com.lingdong.learning.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 将已认证但无权限的请求转换为统一 403 响应。 */
@Component
public class AccessDeniedResponseHandler implements AccessDeniedHandler {
    private final SecurityErrorResponseWriter responseWriter;

    public AccessDeniedResponseHandler(SecurityErrorResponseWriter responseWriter) {
        this.responseWriter = responseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        responseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN, "ACCESS_DENIED", "无权执行此操作");
    }
}
