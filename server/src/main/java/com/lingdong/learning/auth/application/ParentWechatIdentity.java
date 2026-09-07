package com.lingdong.learning.auth.application;

/** 微信服务端换取的家长小程序身份，sessionKey 只允许在当前调用内短暂存在。 */
public record ParentWechatIdentity(
        String appId,
        String openId,
        String unionId,
        String sessionKey
) {
    public ParentWechatIdentity withoutSessionKey() {
        return new ParentWechatIdentity(appId, openId, unionId, null);
    }
}
