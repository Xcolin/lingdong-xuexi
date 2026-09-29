package com.lingdong.learning.common.web;

import com.lingdong.learning.common.alert.ErrorAlertService;
import com.lingdong.learning.common.security.SecurityErrorResponse;
import com.lingdong.learning.common.security.SecurityErrorResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiExceptionHandlerUnexpectedErrorTest {
    private SecurityErrorResponseFactory errorResponseFactory;
    private ErrorAlertService errorAlertService;
    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        errorResponseFactory = mock(SecurityErrorResponseFactory.class);
        errorAlertService = mock(ErrorAlertService.class);
        when(errorResponseFactory.create(any(), anyString(), anyString()))
                .thenReturn(new SecurityErrorResponse("INTERNAL_ERROR", "系统内部错误，请稍后重试", "trace-9"));
        handler = new ApiExceptionHandler(errorResponseFactory, errorAlertService);
    }

    @Test
    void returnsUnified500WithoutLeakingInternalDetailsAndRaisesAlert() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/students");
        RuntimeException original = new RuntimeException("数据库密码 hunter2 泄露细节");

        ResponseEntity<SecurityErrorResponse> response = handler.handleUnexpected(original, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getFirst("X-Request-Id")).isEqualTo("trace-9");
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).doesNotContain("hunter2");
        verify(errorAlertService).recordServerError(eq("/api/v1/students"), eq("trace-9"), eq(RuntimeException.class));
    }
}
