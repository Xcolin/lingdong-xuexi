package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;

/** 教师提交异常报备命令。 */
public record CreateExceptionReportCommand(
        Long classOrganizationId,
        Long studentId,
        ExceptionReportType exceptionType,
        String content,
        String idempotencyKey
) {
}
