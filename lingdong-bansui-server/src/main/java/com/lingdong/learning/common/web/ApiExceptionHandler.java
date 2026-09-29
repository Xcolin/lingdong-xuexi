package com.lingdong.learning.common.web;

import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureDisabledException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将控制器的已知业务异常转换为统一且不泄露内部细节的 JSON 响应；未预期异常走告警兜底。 */
@Order(Ordered.LOWEST_PRECEDENCE)
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final SecurityErrorResponseFactory errorResponseFactory;
    private final ErrorAlertService errorAlertService;

    public ApiExceptionHandler(SecurityErrorResponseFactory errorResponseFactory, ErrorAlertService errorAlertService) {
        this.errorResponseFactory = errorResponseFactory;
        this.errorAlertService = errorAlertService;
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

    @ExceptionHandler(com.lingdong.learning.feature.application.FeatureToggleConflictException.class)
    public ResponseEntity<SecurityErrorResponse> handleFeatureConflict(
            com.lingdong.learning.feature.application.FeatureToggleConflictException exception,HttpServletRequest request) {
        return response(HttpStatus.CONFLICT,"FEATURE_TOGGLE_CONFLICT",exception.getMessage(),request);
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<SecurityErrorResponse> handleFeatureDisabled(
            FeatureDisabledException exception, HttpServletRequest request
    ) {
        return response(HttpStatus.CONFLICT, "FEATURE_DISABLED", "功能暂不可用", request);
    }

    /** 未预期异常兜底：对外统一 500 不泄露内部细节，对内记录完整日志并触发错误告警。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<SecurityErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        SecurityErrorResponse body = errorResponseFactory.create(request, "INTERNAL_ERROR", "系统内部错误，请稍后重试");
        LOGGER.error("未预期异常 traceId={} path={}", body.traceId(), request.getRequestURI(), exception);
        errorAlertService.recordServerError(request.getRequestURI(), body.traceId(), exception.getClass());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header("X-Request-Id", body.traceId())
                .body(body);
    }

    /** Spring MVC 标准畸形请求（路径参数类型不匹配、缺少必选参数）保持 400 语义，不触发告警。 */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<SecurityErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数不合法", request);
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
