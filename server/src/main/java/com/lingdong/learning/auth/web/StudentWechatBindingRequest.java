package com.lingdong.learning.auth.web;

/** 学生微信最终短信绑定请求。 */
public record StudentWechatBindingRequest(
        String verificationTicket,
        String smsCode,
        String deviceId,
        String deviceName
) {
}
