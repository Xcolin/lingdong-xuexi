package com.lingdong.learning.student.application;

import com.lingdong.learning.auth.domain.AuthClientType;

import java.time.LocalDateTime;

/** 学生账号成功注销后的不可变审计事实。 */
public record StudentAccountCancellationRecord(
        Long id,
        Long studentId,
        Long studentUserId,
        Long organizationId,
        Long operatorUserId,
        String reason,
        AuthClientType clientType,
        LocalDateTime cancelledAt
) {
}

