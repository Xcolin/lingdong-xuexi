package com.lingdong.learning.exceptionreport.application;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;

import java.time.LocalDateTime;

/** 异常报备不可变动作视图。 */
public record ExceptionReportActionView(
        Long id,
        String actionType,
        Long operatorUserId,
        String operatorName,
        ExceptionReportStatus beforeStatus,
        ExceptionReportStatus afterStatus,
        String actionNote,
        LocalDateTime createdAt
) {
}
