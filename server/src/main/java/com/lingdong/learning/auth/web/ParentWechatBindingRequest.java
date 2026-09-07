package com.lingdong.learning.auth.web;

/** 首次微信授权后使用手机号验证码完成绑定的请求。 */
public record ParentWechatBindingRequest(
        String bindingTicket,
        String mobile,
        String smsCode,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion
) {
}
