package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 学生连续登录码错误达到阈值后的临时锁定。 */
public class StudentAccountLockedException extends RuntimeException {
    private final LocalDateTime lockedUntil;

    public StudentAccountLockedException(LocalDateTime lockedUntil) {
        super("学生账号暂时锁定");
        this.lockedUntil = lockedUntil;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
