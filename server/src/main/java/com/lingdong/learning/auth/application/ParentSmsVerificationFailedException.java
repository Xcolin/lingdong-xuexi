package com.lingdong.learning.auth.application;

/** 验证码无效、过期或已使用时统一抛出的中性异常。 */
public class ParentSmsVerificationFailedException extends RuntimeException {
    public ParentSmsVerificationFailedException() {
        super("验证码无效或已过期");
    }
}
