package com.lingdong.learning.auth.application;

import java.time.Instant;

/** 主监护人短信已发送后的脱敏绑定挑战。 */
public record StudentWechatBindingCodeChallenge(
        String verificationTicket,
        String maskedMobile,
        Instant expiresAt,
        long retryAfterSeconds
) {
}
