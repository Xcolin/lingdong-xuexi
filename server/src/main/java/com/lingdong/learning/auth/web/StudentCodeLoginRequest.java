package com.lingdong.learning.auth.web;

/** 学生登录请求；账号和登录码格式由认证服务统一转成不泄露的认证失败。 */
public record StudentCodeLoginRequest(
        String studentAccount,
        String loginCode,
        String deviceId,
        String deviceName,
        String captchaChallengeId,
        String captchaAnswer
) {
}
