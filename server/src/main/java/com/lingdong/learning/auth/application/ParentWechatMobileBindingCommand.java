package com.lingdong.learning.auth.application;

/** 已通过微信票据前置校验、等待手机号短信确认的家长账号请求。 */
public record ParentWechatMobileBindingCommand(
        String mobile,
        String smsCode,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion,
        String sourceAddressHash
) {
}
