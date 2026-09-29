package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长手机号验证码注册或登录请求。 */
public record ParentSmsLoginRequest(
        String mobile,
        String code,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion
) {
}
