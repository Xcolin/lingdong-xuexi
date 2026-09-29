package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 家长手机号换绑不可变审计记录，仅保存新旧手机号摘要。 */
public record ParentMobileChangeRecord(
        Long id,
        Long userId,
        String oldMobileDigest,
        String newMobileDigest,
        AuthClientType clientType,
        LocalDateTime changedAt
) {
}
