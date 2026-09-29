package com.lingdong.learning.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 由 Spring Security 过滤链直接输出 JSON 错误。 */
@Component
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;
    private final SecurityErrorResponseFactory responseFactory;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper, SecurityErrorResponseFactory responseFactory) {
        this.objectMapper = objectMapper;
        this.responseFactory = responseFactory;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, int status, String code, String message)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        SecurityErrorResponse body = responseFactory.create(request, code, message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-Id", body.traceId());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
