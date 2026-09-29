package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.CreateExceptionReportCommand;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 教师创建异常报备请求。 */
public record CreateExceptionReportRequest(
        @NotNull Long classOrganizationId,
        @NotNull Long studentId,
        @NotNull ExceptionReportType exceptionType,
        @NotBlank @Size(max = 1000) String content,
        @NotBlank @Size(min = 8, max = 64) String idempotencyKey
) {
    CreateExceptionReportCommand toCommand() {
        return new CreateExceptionReportCommand(classOrganizationId, studentId, exceptionType, content, idempotencyKey);
    }
}
