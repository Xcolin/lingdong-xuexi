package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.AuthenticationFailedException;
import com.lingdong.learning.auth.application.PasswordChangeRejectedException;
import com.lingdong.learning.auth.application.AuthProtectionUnavailableException;
import com.lingdong.learning.auth.application.CaptchaRequiredException;
import com.lingdong.learning.auth.application.RateLimitedException;
import com.lingdong.learning.auth.application.StudentAccountLockedException;
import com.lingdong.learning.auth.application.StudentAuthenticationFailedException;
import com.lingdong.learning.auth.application.StudentQrTicketInvalidException;
import com.lingdong.learning.auth.application.StudentWechatBindingUnavailableException;
import com.lingdong.learning.auth.application.StudentWechatTicketInvalidException;
import com.lingdong.learning.auth.application.ParentAgreementAcceptanceRequiredException;
import com.lingdong.learning.auth.application.ParentSmsVerificationFailedException;
import com.lingdong.learning.auth.application.SmsDeliveryUnavailableException;
import com.lingdong.learning.auth.application.ParentWechatBindingTicketInvalidException;
import com.lingdong.learning.auth.application.ParentMobileChangeTicketInvalidException;
import com.lingdong.learning.auth.application.ParentMobileChangeConflictException;
import com.lingdong.learning.auth.application.ParentAccountCancellationConflictException;
import com.lingdong.learning.auth.infrastructure.wechat.WechatAuthenticationUnavailableException;
import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.student.application.StudentAccountCancellationConflictException;
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

    @ExceptionHandler(PasswordChangeRejectedException.class)
    public ResponseEntity<SecurityErrorResponse> handlePasswordChangeRejected(
            PasswordChangeRejectedException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.BAD_REQUEST, "PASSWORD_CHANGE_REJECTED", exception.getMessage(), request);
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

    @ExceptionHandler(StudentWechatTicketInvalidException.class)
    public ResponseEntity<SecurityErrorResponse> handleStudentWechatTicketInvalid(
            StudentWechatTicketInvalidException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.GONE, "STUDENT_WECHAT_TICKET_INVALID",
                "学生微信绑定凭证无效，请重新授权", request);
    }

    @ExceptionHandler(StudentWechatBindingUnavailableException.class)
    public ResponseEntity<SecurityErrorResponse> handleStudentWechatBindingUnavailable(
            StudentWechatBindingUnavailableException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "STUDENT_WECHAT_BINDING_UNAVAILABLE",
                exception.getMessage(), request);
    }

    @ExceptionHandler(ParentSmsVerificationFailedException.class)
    public ResponseEntity<SecurityErrorResponse> handleParentSmsVerificationFailed(
            ParentSmsVerificationFailedException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.UNAUTHORIZED, "PARENT_SMS_VERIFICATION_FAILED", "验证码无效或已过期", request);
    }

    @ExceptionHandler(ParentAgreementAcceptanceRequiredException.class)
    public ResponseEntity<SecurityErrorResponse> handleParentAgreementAcceptanceRequired(
            ParentAgreementAcceptanceRequiredException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.PRECONDITION_REQUIRED, "PARENT_AGREEMENT_REQUIRED", "请先同意当前用户协议", request);
    }

    @ExceptionHandler(SmsDeliveryUnavailableException.class)
    public ResponseEntity<SecurityErrorResponse> handleSmsDeliveryUnavailable(
            SmsDeliveryUnavailableException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "SMS_DELIVERY_UNAVAILABLE", "短信服务暂不可用", request);
    }

    @ExceptionHandler(ParentWechatBindingTicketInvalidException.class)
    public ResponseEntity<SecurityErrorResponse> handleWechatBindingTicketInvalid(
            ParentWechatBindingTicketInvalidException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.GONE, "PARENT_WECHAT_BINDING_TICKET_INVALID", "微信绑定凭证无效，请重新授权", request);
    }

    @ExceptionHandler(ParentMobileChangeTicketInvalidException.class)
    public ResponseEntity<SecurityErrorResponse> handleMobileChangeTicketInvalid(
            ParentMobileChangeTicketInvalidException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.GONE, "PARENT_MOBILE_CHANGE_TICKET_INVALID", "手机号变更验证已失效", request);
    }

    @ExceptionHandler(ParentMobileChangeConflictException.class)
    public ResponseEntity<SecurityErrorResponse> handleMobileChangeConflict(
            ParentMobileChangeConflictException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "PARENT_MOBILE_CHANGE_CONFLICT", "手机号变更条件已发生变化", request);
    }

    @ExceptionHandler(ParentAccountCancellationConflictException.class)
    public ResponseEntity<SecurityErrorResponse> handleCancellationConflict(
            ParentAccountCancellationConflictException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "PARENT_ACCOUNT_CANCELLATION_CONFLICT", exception.getMessage(), request);
    }

    @ExceptionHandler(StudentAccountCancellationConflictException.class)
    public ResponseEntity<SecurityErrorResponse> handleStudentCancellationConflict(
            StudentAccountCancellationConflictException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "STUDENT_ACCOUNT_CANCELLATION_CONFLICT",
                exception.getMessage(), request);
    }

    @ExceptionHandler(WechatAuthenticationUnavailableException.class)
    public ResponseEntity<SecurityErrorResponse> handleWechatUnavailable(
            WechatAuthenticationUnavailableException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, "WECHAT_AUTH_UNAVAILABLE", "微信认证暂不可用，请使用手机号登录", request);
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
