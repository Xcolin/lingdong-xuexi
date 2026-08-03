package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.util.List;

/** 经验证的请求身份，不包含密码或任何原始令牌。 */
public record AuthenticatedUser(
        Long userId,
        Long sessionId,
        String username,
        String displayName,
        AuthClientType clientType,
        List<String> roleCodes
) {
    public AuthenticatedUser {
        roleCodes = List.copyOf(roleCodes);
    }
}
