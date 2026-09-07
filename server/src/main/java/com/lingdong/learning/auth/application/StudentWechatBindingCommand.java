package com.lingdong.learning.auth.application;

/** 学生微信最终短信绑定命令。 */
public record StudentWechatBindingCommand(
        String verificationTicket,
        String smsCode,
        String deviceId,
        String deviceName
) {
}
