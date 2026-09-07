package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 不保存号码明文和验证码的机构人工换绑审计事实。 */
public record ParentMobileManualRecoveryRecord(
        Long id,
        Long parentUserId,
        Long studentId,
        Long organizationId,
        Long operatorUserId,
        String oldMobileDigest,
        String newMobileDigest,
        String reason,
        AuthClientType clientType,
        LocalDateTime recoveredAt
) {
}
