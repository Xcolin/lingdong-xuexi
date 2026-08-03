package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 面向设备管理页面的会话摘要，不暴露令牌摘要。 */
public record DeviceSession(
        Long id,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        LocalDateTime lastActiveAt
) { }
