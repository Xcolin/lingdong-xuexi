package com.lingdong.learning.importjob.web;

import com.lingdong.learning.importjob.application.ImportJobView;
import com.lingdong.learning.importjob.domain.ImportJobStatus;

import java.time.LocalDateTime;

/** 不暴露存储实现信息的导入校验作业响应。 */
public record ImportJobResponse(
        String id,
        String jobCode,
        String templateId,
        String templateVersion,
        String templateName,
        String sourceFileId,
        String errorFileId,
        String requesterId,
        String organizationId,
        ImportJobStatus status,
        long versionNo,
        String failureCode,
        String failureMessage,
        int totalRows,
        int processedRows,
        int validRows,
        int invalidRows,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ImportJobResponse from(ImportJobView job) {
        return new ImportJobResponse(
                text(job.id()), job.jobCode(), text(job.templateId()), job.templateVersion(),
                job.templateName(), text(job.sourceFileId()), text(job.errorFileId()),
                text(job.requesterId()), text(job.organizationId()), job.status(), job.versionNo(),
                job.failureCode(), job.failureMessage(), job.totalRows(), job.processedRows(),
                job.validRows(), job.invalidRows(), job.queuedAt(), job.startedAt(),
                job.completedAt(), job.createdAt(), job.updatedAt());
    }

    private static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
