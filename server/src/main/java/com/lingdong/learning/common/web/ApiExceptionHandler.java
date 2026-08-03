package com.lingdong.learning.common.web;

import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将 IAM 控制器的已知业务异常转换为统一且不泄露内部细节的 JSON 响应。 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private final SecurityErrorResponseFactory errorResponseFactory;

    public ApiExceptionHandler(SecurityErrorResponseFactory errorResponseFactory) {
        this.errorResponseFactory = errorResponseFactory;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<SecurityErrorResponse> handleResourceNotFound(
            ResourceNotFoundException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在或不可访问", request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<SecurityErrorResponse> handleValidationException(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法", request);
    }

    @ExceptionHandler(SystemOperationAccessDeniedException.class)
    public ResponseEntity<SecurityErrorResponse> handleSystemOperationAccessDenied(
            SystemOperationAccessDeniedException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权执行此操作", request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<SecurityErrorResponse> handleStateConflict(IllegalStateException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "STATE_CONFLICT", "当前状态不允许执行此操作", request);
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<SecurityErrorResponse> handleFeatureDisabled(
            FeatureDisabledException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "FEATURE_DISABLED", "功能暂不可用", request);
    }

    private ResponseEntity<SecurityErrorResponse> response(
            HttpStatus status, String code, String message, HttpServletRequest request
    ) {
        SecurityErrorResponse body = errorResponseFactory.create(request, code, message);
        return ResponseEntity.status(status)
                .header("X-Request-Id", body.traceId())
                .body(body);
    }
}
