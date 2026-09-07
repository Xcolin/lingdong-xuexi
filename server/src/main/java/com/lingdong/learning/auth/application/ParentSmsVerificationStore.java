package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.Duration;

/** 保存验证码摘要并提供跨实例原子限流与单次消费能力。 */
public interface ParentSmsVerificationStore {
    void checkIssueRate(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType, String sourceDigest);

    void checkVerificationRate(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType);

    void save(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType, String codeDigest, Duration ttl);

    boolean consumeIfMatches(
            String mobileDigest,
            ParentSmsPurpose purpose,
            AuthClientType clientType,
            String expectedCodeDigest
    );

    void remove(String mobileDigest, ParentSmsPurpose purpose, AuthClientType clientType);
}
