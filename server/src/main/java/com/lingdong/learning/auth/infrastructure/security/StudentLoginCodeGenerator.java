package com.lingdong.learning.auth.infrastructure.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

/** 使用安全随机源签发允许前导零的4位学生登录码。 */
@Component
public class StudentLoginCodeGenerator {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return String.format(Locale.ROOT, "%04d", secureRandom.nextInt(10_000));
    }
}
