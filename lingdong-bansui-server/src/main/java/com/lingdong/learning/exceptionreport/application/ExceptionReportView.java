package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;
import com.lingdong.learning.exceptionreport.domain.ExceptionReportType;

import java.time.LocalDateTime;

/** 已按数据范围裁剪和脱敏的异常报备视图。 */
public record ExceptionReportView(
        Long id,
        Long studentId,
        String studentName,
        String studentAccountMasked,
        Long classOrganizationId,
        String className,
        Long reporterUserId,
        String reporterName,
        ExceptionReportType exceptionType,
        String content,
        ExceptionReportStatus status,
        Long handledBy,
        String handlerName,
        LocalDateTime reportedAt,
        LocalDateTime handledAt,
        long versionNo
) {
}
