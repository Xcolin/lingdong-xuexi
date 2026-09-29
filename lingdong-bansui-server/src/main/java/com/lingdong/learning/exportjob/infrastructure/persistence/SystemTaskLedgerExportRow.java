package com.lingdong.learning.exportjob.infrastructure.persistence;
import java.time.LocalDateTime;
/** 只选择六列公开审计事实，绝不加载任务描述或业务载荷。 */
public record SystemTaskLedgerExportRow(Long id, String taskType, Long submitterId, Long reviewerId,
        String status, LocalDateTime createdAt, String reviewComment) { }
