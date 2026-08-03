package com.lingdong.learning.auth.application;

/** 小程序学生账号与登录码登录命令。 */
public record StudentCodeLoginCommand(
        String studentAccount,
        String loginCode,
        String deviceId,
        String deviceName,
        String captchaChallengeId,
        String captchaAnswer,
        String sourceAddress
) {
}
