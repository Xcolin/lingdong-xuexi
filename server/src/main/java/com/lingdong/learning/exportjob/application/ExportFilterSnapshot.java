package com.lingdong.learning.exportjob.application;

import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;

import java.time.LocalDateTime;

/** 作业创建时固化的规范化筛选条件。 */
public record ExportFilterSnapshot(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        IamChangeAuditEventType eventType
) { }
