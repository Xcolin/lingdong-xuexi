package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.exportjob.domain.ExportJobType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;
import java.util.List;

/** 创建导出作业的受控应用命令。 */
public record CreateExportJobCommand(
        Long requesterId,
        ExportJobType exportType,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType,
        List<String> selectedColumns,
        String reason,
        String requestSource
) { }
