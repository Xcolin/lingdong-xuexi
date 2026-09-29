package com.lingdong.learning.auth.infrastructure.security;

import com.lingdong.learning.auth.application.ParentSmsCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 使用密码学安全随机数生成固定六位数字验证码。 */
@Component
@Profile("!test")
public class SecureParentSmsCodeGenerator implements ParentSmsCodeGenerator {
    private static final int CODE_BOUND = 1_000_000;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return "%06d".formatted(secureRandom.nextInt(CODE_BOUND));
    }
}
