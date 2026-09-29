package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.infrastructure.captcha.CaptchaImageGenerator;
import com.lingdong.learning.auth.infrastructure.captcha.GeneratedCaptchaImage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 测试环境固定验证码图片，避免测试读取或输出真实答案。 */
@Component
@Profile("test")
public class TestCaptchaImageGenerator implements CaptchaImageGenerator {
    @Override
    public GeneratedCaptchaImage generate() {
        return new GeneratedCaptchaImage("AB12", "data:image/png;base64,dGVzdA==");
    }
}
