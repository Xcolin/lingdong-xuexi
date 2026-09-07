package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.domain.AccountSecurityEventStatus;
import com.lingdong.learning.auth.domain.AccountSecurityEventType;
import com.lingdong.learning.auth.domain.AccountSecurityRiskLevel;
import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 当前用户可见的脱敏账号安全事件响应。 */
public record AccountSecurityEventResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        AccountSecurityEventType eventType,
        AccountSecurityRiskLevel riskLevel,
        AuthClientType clientType,
        String deviceName,
        AccountSecurityEventStatus status,
        LocalDateTime occurredAt,
        LocalDateTime readAt
) {
}
