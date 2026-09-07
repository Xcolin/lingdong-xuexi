package com.lingdong.learning.exportjob.application.adapter;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;

/** 固化后的受控导出筛选，不包含客户端字段名或查询表达式。 */
public record ExportRequestDefinition(
        Long requesterId,
        Long studentId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType
) { }
