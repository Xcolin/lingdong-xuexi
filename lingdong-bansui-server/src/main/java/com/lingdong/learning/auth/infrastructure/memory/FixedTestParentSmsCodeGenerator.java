package com.lingdong.learning.auth.infrastructure.memory;

import com.lingdong.learning.auth.application.ParentSmsCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** 测试专用固定验证码生成器，避免通过响应或日志泄露验证码。 */
@Component
@Profile("test")
public class FixedTestParentSmsCodeGenerator implements ParentSmsCodeGenerator {
    public static final String CODE = "384291";

    @Override
    public String generate() {
        return CODE;
    }
}
