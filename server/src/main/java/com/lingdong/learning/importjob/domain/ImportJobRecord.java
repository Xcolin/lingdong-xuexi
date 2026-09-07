package com.lingdong.learning.importjob.domain;

import java.time.LocalDateTime;

/** 导入校验作业的完整持久化记录。 */
public record ImportJobRecord(
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
        Integer totalRows,
        Integer processedRows,
        Integer validRows,
        Integer invalidRows,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
