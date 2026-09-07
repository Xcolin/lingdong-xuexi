package com.lingdong.learning.studentimport.domain;

import java.time.LocalDateTime;

/** 学员导入执行、处理计数和凭证生命周期事实。 */
public record StudentImportExecutionRecord(
        Long id,
        String executionCode,
        Long validationJobId,
        Long requesterId,
        Long organizationId,
        Long classOrganizationId,
        StudentImportExecutionStatus status,
        Long versionNo,
        Integer totalRows,
        Integer processedRows,
        Integer succeededRows,
        Integer failedRows,
        String failureCode,
        String failureMessage,
        Long credentialFileId,
        StudentImportCredentialStatus credentialStatus,
        LocalDateTime credentialExpiresAt,
        LocalDateTime credentialDownloadedAt,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
