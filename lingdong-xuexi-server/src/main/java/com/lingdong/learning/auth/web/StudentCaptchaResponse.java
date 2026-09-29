package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.IssuedCaptchaChallenge;

import java.time.Instant;

/** 客户端可见的图形验证码挑战，不返回答案摘要或绑定信息。 */
public record StudentCaptchaResponse(String challengeId, String imageBase64, Instant expiresAt) {
    static StudentCaptchaResponse from(IssuedCaptchaChallenge challenge) {
        return new StudentCaptchaResponse(challenge.challengeId(), challenge.imageBase64(), challenge.expiresAt());
    }
}
