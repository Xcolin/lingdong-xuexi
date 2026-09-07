package com.lingdong.learning.iam.web;

import com.lingdong.learning.iam.audit.application.IamChangeAudit;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;

import java.time.LocalDateTime;

/** 身份权限审计响应，雪花标识显式转换为字符串以避免前端精度丢失。 */
public record IamChangeAuditResponse(
        String id, IamChangeAuditEventType eventType, String operatorId, IamChangeTargetType targetType,
        String targetId, String relatedId, String organizationId, String beforeValue, String afterValue,
        LocalDateTime occurredAt
) {
    static IamChangeAuditResponse from(IamChangeAudit audit) {
        return new IamChangeAuditResponse(
                audit.id().toString(), audit.eventType(), text(audit.operatorId()), audit.targetType(),
                audit.targetId().toString(), text(audit.relatedId()), text(audit.organizationId()),
                audit.beforeValue(), audit.afterValue(), audit.occurredAt()
        );
    }

    private static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
