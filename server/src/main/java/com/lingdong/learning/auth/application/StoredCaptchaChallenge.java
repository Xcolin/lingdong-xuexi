package com.lingdong.learning.auth.application;

import java.time.Instant;

/** 验证码保护存储中的摘要和绑定数据。 */
public record StoredCaptchaChallenge(
        String answerHash,
        String accountDigest,
        String deviceDigest,
        Instant expiresAt
) {
}
