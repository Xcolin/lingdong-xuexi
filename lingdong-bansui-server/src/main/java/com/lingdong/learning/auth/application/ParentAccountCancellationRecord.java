package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 家长账号注销申请持久化记录。 */
public record ParentAccountCancellationRecord(
        Long id,
        Long userId,
        ParentAccountCancellationStatus status,
        String activeScopeKey,
        LocalDateTime requestedAt,
        LocalDateTime coolingEndsAt,
        LocalDateTime revokedAt,
        LocalDateTime finalizedAt
) {
}
