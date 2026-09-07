package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 家长对指定版本用户协议的不可覆盖接受事实。 */
public record ParentAgreementAcceptance(
        Long id,
        Long userId,
        String agreementVersion,
        AuthClientType clientType,
        String sourceAddressHash,
        LocalDateTime acceptedAt
) {
}
