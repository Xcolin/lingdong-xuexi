package com.lingdong.learning.auth.application;

/** 小程序学生扫码后提交 4 位登录码的命令。 */
public record StudentQrLoginCommand(
        String qrContent,
        String loginCode,
        String deviceId,
        String deviceName,
        String captchaChallengeId,
        String captchaAnswer,
        String sourceAddress
) {
}
