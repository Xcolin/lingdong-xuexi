package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.StudentWechatBindingCodeChallenge;

import java.time.Instant;

/** 学生微信绑定短信发送后的脱敏挑战响应。 */
public record StudentWechatBindingCodeResponse(
        String verificationTicket,
        String maskedMobile,
        Instant expiresAt,
        long retryAfterSeconds
) {
    static StudentWechatBindingCodeResponse from(StudentWechatBindingCodeChallenge challenge) {
        return new StudentWechatBindingCodeResponse(
                challenge.verificationTicket(), challenge.maskedMobile(),
                challenge.expiresAt(), challenge.retryAfterSeconds());
    }
}
