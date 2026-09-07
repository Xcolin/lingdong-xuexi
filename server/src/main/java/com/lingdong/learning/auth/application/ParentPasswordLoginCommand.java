package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长使用手机号和密码从指定前端应用建立会话。 */
public record ParentPasswordLoginCommand(
        String mobile,
        String password,
        AuthClientType clientType,
        String deviceId,
        String deviceName
) {
}
