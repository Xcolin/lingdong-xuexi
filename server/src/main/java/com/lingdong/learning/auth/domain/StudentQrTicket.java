package com.lingdong.learning.auth.domain;

import java.time.LocalDateTime;

/** 数据库中的学生扫码登录票据，不保存或返回原始随机票据。 */
public record StudentQrTicket(
        Long id,
        Long studentId,
        Long studentUserId,
        String tokenHash,
        StudentQrTicketStatus status,
        LocalDateTime expiresAt,
        LocalDateTime consumedAt,
        Long issuedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
