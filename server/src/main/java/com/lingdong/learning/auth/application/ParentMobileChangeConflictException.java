package com.lingdong.learning.auth.application;

/** 新手机号冲突或账号手机号已发生并发变化。 */
public class ParentMobileChangeConflictException extends RuntimeException {
    public ParentMobileChangeConflictException() {
        super("手机号变更条件已发生变化，请重新操作");
    }

    public ParentMobileChangeConflictException(Throwable cause) {
        super("手机号变更条件已发生变化，请重新操作", cause);
    }
}
