package com.lingdong.learning.exceptionreport.web;

import com.lingdong.learning.exceptionreport.application.ExceptionReportActionView;

import java.time.LocalDateTime;

/** 异常报备不可变动作响应。 */
public record ExceptionReportActionResponse(
        String id, String actionType, String operatorUserId, String operatorName,
        String beforeStatus, String afterStatus, String actionNote, LocalDateTime createdAt
) {
    static ExceptionReportActionResponse from(ExceptionReportActionView v) {
        return new ExceptionReportActionResponse(String.valueOf(v.id()), v.actionType(),
                String.valueOf(v.operatorUserId()), v.operatorName(),
                v.beforeStatus() == null ? null : v.beforeStatus().name(), v.afterStatus().name(),
                v.actionNote(), v.createdAt());
    }
}
