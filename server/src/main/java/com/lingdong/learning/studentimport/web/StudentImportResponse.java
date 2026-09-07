package com.lingdong.learning.studentimport.web;

import com.lingdong.learning.studentimport.application.StudentImportView;
import com.lingdong.learning.studentimport.domain.StudentImportCredentialStatus;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionRecord;
import com.lingdong.learning.studentimport.domain.StudentImportExecutionStatus;

import java.time.LocalDateTime;

/** 雪花标识使用字符串且不暴露密文或附件内部标识的执行响应。 */
public record StudentImportResponse(
        String id,
        String executionCode,
        String validationJobId,
        String organizationId,
        String classOrganizationId,
        StudentImportExecutionStatus status,
        long versionNo,
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
    static StudentImportResponse from(StudentImportExecutionRecord execution) {
        return from(StudentImportView.from(execution));
    }

    static StudentImportResponse from(StudentImportView execution) {
        return new StudentImportResponse(
                execution.id().toString(), execution.executionCode(),
                execution.validationJobId().toString(), execution.organizationId().toString(),
                string(execution.classOrganizationId()), execution.status(), execution.versionNo(),
                execution.totalRows(), execution.processedRows(), execution.succeededRows(),
                execution.failedRows(), execution.failureCode(), execution.failureMessage(),
                execution.credentialStatus(), execution.credentialExpiresAt(),
                execution.credentialDownloadedAt(), execution.queuedAt(), execution.startedAt(),
                execution.completedAt(), execution.createdAt());
    }

    private static String string(Long value) {
        return value == null ? null : value.toString();
    }
}
