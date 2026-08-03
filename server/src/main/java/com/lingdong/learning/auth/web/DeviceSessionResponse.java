package com.lingdong.learning.auth.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 设备会话列表响应，不暴露令牌及其摘要。 */
public record DeviceSessionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        LocalDateTime accessExpiresAt,
        LocalDateTime refreshExpiresAt,
        LocalDateTime lastActiveAt
) { }
