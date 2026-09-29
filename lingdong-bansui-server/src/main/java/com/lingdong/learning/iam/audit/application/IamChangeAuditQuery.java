package com.lingdong.learning.iam.audit.application;

import java.time.LocalDateTime;

/** 已校验并换算为数据库偏移量的审计查询条件。 */
public record IamChangeAuditQuery(
        IamChangeAuditEventType eventType,
        IamChangeTargetType targetType,
        Long operatorId,
        Long targetId,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int offset,
        int limit
) { }
