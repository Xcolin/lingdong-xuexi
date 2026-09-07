package com.lingdong.learning.auth.application;

/** 首次微信授权后的手机号绑定请求。 */
public record ParentWechatBindingCommand(
        String bindingTicket,
        String mobile,
        String smsCode,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion,
        String sourceAddressHash
) {
}
