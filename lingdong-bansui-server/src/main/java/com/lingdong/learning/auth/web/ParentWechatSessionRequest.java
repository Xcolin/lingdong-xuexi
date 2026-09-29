package com.lingdong.learning.auth.web;

/** 微信小程序临时凭证换取家长登录状态的请求。 */
public record ParentWechatSessionRequest(
        String temporaryCode,
        String deviceId,
        String deviceName
) {
}
