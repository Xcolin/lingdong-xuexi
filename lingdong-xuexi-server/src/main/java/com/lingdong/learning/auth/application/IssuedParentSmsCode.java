package com.lingdong.learning.auth.application;

import java.time.Instant;

/** 短信发送后的公开元数据，刻意不包含验证码明文。 */
public record IssuedParentSmsCode(Instant expiresAt, long retryAfterSeconds) {
}
