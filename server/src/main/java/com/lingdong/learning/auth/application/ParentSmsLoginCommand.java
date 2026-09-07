package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长手机号验证码注册或登录命令。 */
public record ParentSmsLoginCommand(
        String mobile,
        String code,
        AuthClientType clientType,
        String deviceId,
        String deviceName,
        boolean agreementAccepted,
        String agreementVersion,
        String sourceAddressHash
) {
}
