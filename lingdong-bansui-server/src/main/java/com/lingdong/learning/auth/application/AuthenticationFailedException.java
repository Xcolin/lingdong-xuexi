package com.lingdong.learning.auth.application;

/** 对外统一的认证失败异常，避免泄露账号或凭证具体状态。 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException() {
        super("认证失败");
    }
}
