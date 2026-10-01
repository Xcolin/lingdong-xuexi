package com.lingdong.learning.auth.application;

/** 只承载受控修改密码提示，不包含凭证内容。 */
public class PasswordChangeRejectedException extends IllegalArgumentException {
    public PasswordChangeRejectedException(String message) {
        super(message);
    }
}
