package com.lingdong.learning.auth.domain;

import java.time.LocalDateTime;

/** 设备会话持久化记录，仅保存令牌摘要而不保存原始令牌。 */
public record DeviceSessionRecord(
        Long id,
        Long userId,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        String accessTokenHash,
        String refreshTokenHash,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        DeviceSessionStatus status,
        LocalDateTime lastActiveAt,
        LocalDateTime signedOutAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) { }
