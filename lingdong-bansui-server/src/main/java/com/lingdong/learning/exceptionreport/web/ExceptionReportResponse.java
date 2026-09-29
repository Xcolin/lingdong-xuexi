package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportView;

import java.time.LocalDateTime;

/** 异常报备响应，19 位标识统一按字符串传输。 */
public record ExceptionReportResponse(
        String id, String studentId, String studentName, String studentAccountMasked,
        String classOrganizationId, String className, String reporterUserId, String reporterName,
        String exceptionType, String content, String status, String handledBy, String handlerName,
        LocalDateTime reportedAt, LocalDateTime handledAt, long versionNo
) {
    static ExceptionReportResponse from(ExceptionReportView v) {
        return new ExceptionReportResponse(String.valueOf(v.id()), String.valueOf(v.studentId()),
                v.studentName(), v.studentAccountMasked(), String.valueOf(v.classOrganizationId()),
                v.className(), String.valueOf(v.reporterUserId()), v.reporterName(),
                v.exceptionType().name(), v.content(), v.status().name(),
                v.handledBy() == null ? null : String.valueOf(v.handledBy()), v.handlerName(),
                v.reportedAt(), v.handledAt(), v.versionNo());
    }
}
