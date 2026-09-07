package com.lingdong.learning.importjob.application;

import com.lingdong.learning.importjob.domain.ImportJobStatus;
import com.lingdong.learning.importjob.domain.ImportJobRecord;

import java.time.LocalDateTime;

/** 导入校验作业的安全应用视图。 */
public record ImportJobView(
        Long id,
        String jobCode,
        Long templateId,
        String templateVersion,
        String templateName,
        String fieldMappingSnapshot,
        Long sourceFileId,
        Long errorFileId,
        Long requesterId,
        Long organizationId,
        ImportJobStatus status,
        Long versionNo,
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
    public static ImportJobView from(ImportJobRecord job) {
        return new ImportJobView(
                job.id(), job.jobCode(), job.templateId(), job.templateVersion(), job.templateName(),
                job.fieldMappingSnapshot(), job.sourceFileId(), job.errorFileId(), job.requesterId(),
                job.organizationId(), job.status(), job.versionNo(), job.failureCode(), job.failureMessage(),
                job.totalRows(), job.processedRows(), job.validRows(), job.invalidRows(), job.queuedAt(),
                job.startedAt(), job.completedAt(), job.createdAt(), job.updatedAt()
        );
    }
}
