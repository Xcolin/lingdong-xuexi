package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticationFailedException;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.CaptchaRequiredException;
import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.application.StudentAccountLockedException;
import com.lingdong.learning.auth.application.StudentAuthenticationFailedException;
import com.lingdong.learning.auth.application.StudentQrTicketInvalidException;
import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将认证应用服务异常转换为与安全过滤链一致的 HTTP 错误响应。 */
@RestControllerAdvice
public class AuthenticationExceptionHandler {
    private final SecurityErrorResponseFactory responseFactory;

    public AuthenticationExceptionHandler(SecurityErrorResponseFactory responseFactory) {
        this.responseFactory = responseFactory;
    }

    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<SecurityErrorResponse> handleAuthenticationFailed(
            AuthenticationFailedException exception,
            HttpServletRequest request
    ) {
        SecurityErrorResponse body = responseFactory.create(request, "AUTH_REQUIRED", "认证失败");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("X-Request-Id", body.traceId())
                .body(body);
    }

    @ExceptionHandler(StudentAuthenticationFailedException.class)
    public ResponseEntity<SecurityErrorResponse> handleStudentAuthenticationFailed(
            StudentAuthenticationFailedException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, "STUDENT_AUTH_FAILED", "学生账号或登录码错误", request);
    }

    @ExceptionHandler(CaptchaRequiredException.class)
    public ResponseEntity<SecurityErrorResponse> handleCaptchaRequired(
            CaptchaRequiredException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.PRECONDITION_REQUIRED, "CAPTCHA_REQUIRED", "需要有效的图形验证码", request);
    }

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<SecurityErrorResponse> handleRateLimited(
            RateLimitedException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁", request);
    }

    @ExceptionHandler(AuthProtectionUnavailableException.class)
    public ResponseEntity<SecurityErrorResponse> handleProtectionUnavailable(
            AuthProtectionUnavailableException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_PROTECTION_UNAVAILABLE", "认证保护服务暂不可用", request);
    }

    @ExceptionHandler(StudentAccountLockedException.class)
    public ResponseEntity<StudentAccountLockedResponse> handleStudentAccountLocked(
            StudentAccountLockedException exception, HttpServletRequest request
    ) {
        SecurityErrorResponse error = responseFactory.create(
                request, "STUDENT_ACCOUNT_LOCKED", "学生账号暂时锁定");
        StudentAccountLockedResponse body = new StudentAccountLockedResponse(
                error.code(), error.message(), error.traceId(), exception.getLockedUntil());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .header("X-Request-Id", error.traceId())
                .body(body);
    }

    @ExceptionHandler(StudentQrTicketInvalidException.class)
    public ResponseEntity<SecurityErrorResponse> handleStudentQrTicketInvalid(
            StudentQrTicketInvalidException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.GONE, "STUDENT_QR_TICKET_INVALID", "登录二维码无效，请重新扫码", request);
    }

    private ResponseEntity<SecurityErrorResponse> response(
            HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        SecurityErrorResponse body = responseFactory.create(request, code, message);
        return ResponseEntity.status(status)
                .header("X-Request-Id", body.traceId())
                .body(body);
    }
}
