package com.lingdong.learning.auth.application;

import org.springframework.stereotype.Component;

/** 统一校验平台密码必须为 8 至 20 位字母和数字组合。 */
@Component
public class PasswordPolicy {
    private static final String PATTERN = "(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,20}";

    public void validate(String password) {
        if (password == null || !password.matches(PATTERN)) {
            throw new IllegalArgumentException("密码必须为 8 至 20 位字母和数字组合");
        }
    }
}
