package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.domain.AuthClientType;

/** 家长通过用途隔离的短信验证码重置密码命令。 */
public record ParentPasswordResetCommand(
        String mobile,
        String code,
        String newPassword,
        AuthClientType clientType
) {
}
