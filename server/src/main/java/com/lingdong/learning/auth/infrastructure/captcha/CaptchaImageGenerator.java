package com.lingdong.learning.auth.infrastructure.captcha;

/** 隔离第三方图形验证码组件的最小边界。 */
@FunctionalInterface
public interface CaptchaImageGenerator {
    GeneratedCaptchaImage generate();
}
