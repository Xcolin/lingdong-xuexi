package com.lingdong.learning.responsibility;

import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes=ResponsibilityController.class)
public class ResponsibilityExceptionHandler {
    private final SecurityErrorResponseFactory errors;
    public ResponsibilityExceptionHandler(SecurityErrorResponseFactory errors){this.errors=errors;}
    @ExceptionHandler(ResponsibilityConflictException.class)
    public ResponseEntity<SecurityErrorResponse> conflict(ResponsibilityConflictException error,HttpServletRequest request){
        return ResponseEntity.status(409).body(errors.create(request,"STUDENT_RESPONSIBILITY_CONFLICT",error.getMessage()));
    }
}
