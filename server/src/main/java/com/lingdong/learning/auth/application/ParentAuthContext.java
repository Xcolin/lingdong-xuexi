package com.lingdong.learning.auth.application;

/** 家长认证入口渲染所需的公开能力与协议元数据。 */
public record ParentAuthContext(
        boolean enabled,
        boolean wechatEnabled,
        String agreementVersion,
        long codeExpiresInSeconds,
        long retryAfterSeconds
) {
}
