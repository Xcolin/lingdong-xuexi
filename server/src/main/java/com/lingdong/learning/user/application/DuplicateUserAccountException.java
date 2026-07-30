package com.lingdong.learning.user.application;

/**
 * Raised when an account name or non-empty mobile number is already bound to another user.
 */
public class DuplicateUserAccountException extends RuntimeException {
    public DuplicateUserAccountException(String value) {
        super("用户账号或手机号已存在：" + value);
    }
}
