package com.lingdong.learning.auth.application;

/** 家长微信临时凭证交换请求。 */
public record ParentWechatSessionCommand(
        String temporaryCode,
        String deviceId,
        String deviceName
) {
}
