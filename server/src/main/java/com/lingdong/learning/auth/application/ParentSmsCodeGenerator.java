package com.lingdong.learning.auth.application;

/** 生成仅用于本次短信投递的验证码明文。 */
@FunctionalInterface
public interface ParentSmsCodeGenerator {
    String generate();
}
