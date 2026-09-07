package com.lingdong.learning.studentimport.application;

import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;

import java.time.LocalDateTime;

/** 不包含附件存储和密文信息的学员导入执行视图。 */
public record StudentImportView(
        Long id,
        String executionCode,
        Long validationJobId,
        Long organizationId,
        Long classOrganizationId,
        StudentImportExecutionStatus status,
        Long versionNo,
        int totalRows,
        int processedRows,
        int succeededRows,
        int failedRows,
        String failureCode,
        String failureMessage,
        StudentImportCredentialStatus credentialStatus,
        LocalDateTime credentialExpiresAt,
        LocalDateTime credentialDownloadedAt,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt
) {
    public static StudentImportView from(StudentImportExecutionRecord execution) {
        return new StudentImportView(
                execution.id(), execution.executionCode(), execution.validationJobId(),
                execution.organizationId(), execution.classOrganizationId(), execution.status(),
                execution.versionNo(), execution.totalRows(), execution.processedRows(),
                execution.succeededRows(), execution.failedRows(), execution.failureCode(),
                execution.failureMessage(), execution.credentialStatus(),
                execution.credentialExpiresAt(), execution.credentialDownloadedAt(),
                execution.queuedAt(), execution.startedAt(), execution.completedAt(),
                execution.createdAt());
    }
}
