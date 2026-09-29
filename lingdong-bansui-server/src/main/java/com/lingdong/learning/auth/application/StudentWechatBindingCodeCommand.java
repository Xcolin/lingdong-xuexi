package com.lingdong.learning.auth.application;

/** 学生首次微信绑定的学生登录码预认证命令。 */
public record StudentWechatBindingCodeCommand(
        String bindingTicket,
        String studentAccount,
        String loginCode,
        String deviceId,
        String deviceName,
        String captchaChallengeId,
        String captchaAnswer,
        String sourceAddress
) {
}
