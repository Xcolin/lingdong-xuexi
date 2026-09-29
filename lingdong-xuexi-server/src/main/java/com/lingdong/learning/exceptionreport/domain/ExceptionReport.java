package com.lingdong.learning.exceptionreport.domain;

import java.time.LocalDateTime;

/** 学生异常报备当前事实，处理历史由独立只追加记录保存。 */
public record ExceptionReport(
        Long id,
        Long studentId,
        Long classOrganizationId,
        Long reporterUserId,
        ExceptionReportType exceptionType,
        String content,
        ExceptionReportStatus status,
        String idempotencyKey,
        Long handledBy,
        LocalDateTime handledAt,
        long versionNo,
        LocalDateTime reportedAt
) {
}
