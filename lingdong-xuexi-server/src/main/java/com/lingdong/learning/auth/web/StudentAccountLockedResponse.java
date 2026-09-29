package com.lingdong.learning.auth.web;

import java.time.LocalDateTime;

/** 学生账号锁定错误，除锁定截止时间外不暴露账号状态。 */
public record StudentAccountLockedResponse(
        String code,
        String message,
        String traceId,
        LocalDateTime lockedUntil
) {
}
