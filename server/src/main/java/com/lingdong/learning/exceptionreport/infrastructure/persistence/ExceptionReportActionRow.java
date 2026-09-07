package com.lingdong.learning.exceptionreport.infrastructure.persistence;

import com.lingdong.learning.exceptionreport.domain.ExceptionReportStatus;

import java.time.LocalDateTime;

/** 异常报备动作联表查询行。 */
public record ExceptionReportActionRow(
        Long id, String actionType, Long operatorUserId, String operatorName,
        ExceptionReportStatus beforeStatus, ExceptionReportStatus afterStatus,
        String actionNote, LocalDateTime createdAt
) {
}
