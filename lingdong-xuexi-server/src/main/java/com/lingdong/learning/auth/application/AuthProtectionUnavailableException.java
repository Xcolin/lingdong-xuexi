package com.lingdong.learning.auth.application;

/** 验证码或限流保护存储不可用，认证入口必须失败关闭。 */
public class AuthProtectionUnavailableException extends RuntimeException {
    public AuthProtectionUnavailableException(Throwable cause) {
        super("认证保护服务暂不可用", cause);
    }
}
