package com.lingdong.learning.iam.audit.application;

import java.time.LocalDateTime;

/** 不包含个人敏感明文的身份权限变更事实。 */
public record IamChangeAudit(
        Long id,
        IamChangeAuditEventType eventType,
        Long operatorId,
        IamChangeTargetType targetType,
        Long targetId,
        Long relatedId,
        Long organizationId,
        String beforeValue,
        String afterValue,
        LocalDateTime occurredAt
) { }
