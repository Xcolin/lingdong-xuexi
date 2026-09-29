package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobRowErrorView;
import com.lingdong.learning.importjob.domain.ImportJobRowStatus;

import java.time.LocalDateTime;

/** 不包含原始单元格值的错误行响应。 */
public record ImportJobErrorResponse(
        String id,
        int rowNumber,
        ImportJobRowStatus status,
        String errorSummary,
        LocalDateTime createdAt
) {
    public static ImportJobErrorResponse from(ImportJobRowErrorView row) {
        return new ImportJobErrorResponse(
                row.id().toString(), row.rowNumber(), row.status(), row.errorSummary(), row.createdAt());
    }
}
