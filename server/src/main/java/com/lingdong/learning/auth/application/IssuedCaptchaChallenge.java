package com.lingdong.learning.auth.application;

import java.time.Instant;

/** 返回客户端的一次性验证码挑战，不包含答案。 */
public record IssuedCaptchaChallenge(String challengeId, String imageBase64, Instant expiresAt) {
}
