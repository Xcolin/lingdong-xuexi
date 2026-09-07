package com.lingdong.learning.auth.web;

/** 学生微信首次绑定的学生登录码校验与短信发送请求。 */
public record StudentWechatBindingCodeRequest(
        String bindingTicket,
        String studentAccount,
        String loginCode,
        String deviceId,
        String deviceName,
        String captchaChallengeId,
        String captchaAnswer
) {
}
