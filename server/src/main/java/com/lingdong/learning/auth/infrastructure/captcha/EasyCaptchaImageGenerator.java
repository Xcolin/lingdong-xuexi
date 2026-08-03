package com.lingdong.learning.auth.infrastructure.captcha;

import com.wf.captcha.SpecCaptcha;
import com.wf.captcha.base.Captcha;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

/** 使用 EasyCaptcha 生成图片；挑战状态和安全校验由应用服务负责。 */
@Component
@Profile("!test")
public class EasyCaptchaImageGenerator implements CaptchaImageGenerator {
    @Override
    public GeneratedCaptchaImage generate() {
        SpecCaptcha captcha = new SpecCaptcha(130, 48, 4);
        captcha.setCharType(Captcha.TYPE_DEFAULT);
        return new GeneratedCaptchaImage(captcha.text(), captcha.toBase64());
    }
}
