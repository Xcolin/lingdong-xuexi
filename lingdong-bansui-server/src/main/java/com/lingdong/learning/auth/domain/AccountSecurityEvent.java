package com.lingdong.learning.auth.domain;

import java.time.LocalDateTime;

/** 账号安全事件持久化事实，设备摘要和范围键不得向客户端暴露。 */
public record AccountSecurityEvent(
        Long id,
        Long userId,
        Long sessionId,
        AccountSecurityEventType eventType,
        AccountSecurityRiskLevel riskLevel,
        AuthClientType clientType,
        String deviceName,
        String deviceFingerprintHash,
        String eventScopeKey,
        AccountSecurityEventStatus status,
        LocalDateTime occurredAt,
        LocalDateTime readAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
