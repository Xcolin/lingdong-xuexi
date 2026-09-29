package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 不包含微信标识、手机号或验证码的学生微信绑定审计。 */
public record StudentWechatBindingAudit(
        Long id,
        Long bindingId,
        Long studentId,
        Long studentUserId,
        StudentWechatBindingAuditEvent eventType,
        Long operatorUserId,
        AuthClientType clientType,
        LocalDateTime occurredAt
) {
}
