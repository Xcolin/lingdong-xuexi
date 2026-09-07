package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长通过手机号验证码重置密码请求。 */
public record ParentPasswordResetRequest(
        String mobile,
        String code,
        String newPassword,
        AuthClientType clientType
) {
}
