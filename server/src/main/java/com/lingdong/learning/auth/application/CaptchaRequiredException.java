package com.lingdong.learning.auth.application;

/** 登录请求缺少有效且匹配的一次性图形验证码。 */
public class CaptchaRequiredException extends RuntimeException {
    public CaptchaRequiredException() {
        super("需要有效的图形验证码");
    }
}
