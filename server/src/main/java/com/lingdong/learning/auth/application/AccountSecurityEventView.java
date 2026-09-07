package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AccountSecurityEventStatus;
import com.lingdong.learning.auth.domain.AccountSecurityEventType;
import com.lingdong.learning.auth.domain.AccountSecurityRiskLevel;
import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 面向当前用户的脱敏账号安全事件。 */
public record AccountSecurityEventView(
        Long id,
        AccountSecurityEventType eventType,
        AccountSecurityRiskLevel riskLevel,
        AuthClientType clientType,
        String deviceName,
        AccountSecurityEventStatus status,
        LocalDateTime occurredAt,
        LocalDateTime readAt
) {
}
