package com.lingdong.learning.auth.infrastructure.captcha;

/** 图片组件的一次性生成结果，答案只在服务端短暂使用。 */
public record GeneratedCaptchaImage(String answer, String imageBase64) {
}
