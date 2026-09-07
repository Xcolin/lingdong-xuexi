package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长手机号密码登录请求，客户端类型决定会话所属前端应用。 */
public record ParentPasswordLoginRequest(
        String mobile,
        String password,
        AuthClientType clientType,
        String deviceId,
        String deviceName
) {
}
