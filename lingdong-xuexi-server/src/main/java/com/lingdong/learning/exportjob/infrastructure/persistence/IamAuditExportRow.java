package com.lingdong.learning.exportjob.infrastructure.persistence;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;

import java.time.LocalDateTime;

/** 权限变更日志导出的数据库受控投影。 */
public record IamAuditExportRow(
        Long id,
        LocalDateTime occurredAt,
        IamChangeAuditEventType eventType,
        IamChangeTargetType targetType,
        Long targetId,
        String targetName,
        String operatorName,
        String beforeValue,
        String afterValue
) { }
