package com.lingdong.learning.exportjob.domain;

import java.time.LocalDateTime;

/** 导出请求、权限快照引用和当前执行状态。 */
public record ExportJobRecord(
        Long id,
        String jobCode,
        ExportJobType exportType,
        Long templateId,
        String templateName,
        String templateVersion,
        Long requesterId,
        Long studentId,
        Long systemTaskId,
        String filterSnapshot,
        String columnSnapshot,
        String scopeSnapshot,
        String maskPolicySnapshot,
        String requestReason,
        Boolean sensitive,
        ExportJobStatus status,
        Long versionNo,
        Long resultFileId,
        Long totalRows,
        Long processedRows,
        String failureCode,
        String failureMessage,
        String requestSourceHash,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        LocalDateTime queuedAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
