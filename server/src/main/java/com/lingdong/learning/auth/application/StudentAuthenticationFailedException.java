package com.lingdong.learning.auth.application;

/** 不区分账号、状态或登录码错误的学生认证失败。 */
public class StudentAuthenticationFailedException extends RuntimeException {
    public StudentAuthenticationFailedException() {
        super("学生账号或登录码错误");
    }
}
