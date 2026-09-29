package com.lingdong.learning.exceptionreport.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;

import java.time.LocalDateTime;

/** 异常报备联表查询行。 */
public record ExceptionReportRow(
        Long id, Long studentId, String studentName, String studentAccount,
        Long classOrganizationId, String className, Long reporterUserId, String reporterName,
        ExceptionReportType exceptionType, String content, ExceptionReportStatus status,
        Long handledBy, String handlerName, LocalDateTime reportedAt,
        LocalDateTime handledAt, long versionNo
) {
}
