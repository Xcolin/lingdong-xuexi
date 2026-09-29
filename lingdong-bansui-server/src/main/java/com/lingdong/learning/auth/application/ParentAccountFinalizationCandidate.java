package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 等待最终注销的家长申请快照，只包含调度和事务判断所需字段。 */
public record ParentAccountFinalizationCandidate(
        Long cancellationId,
        Long userId,
        ParentAccountCancellationStatus status,
        String activeScopeKey,
        LocalDateTime requestedAt,
        LocalDateTime coolingEndsAt,
        LocalDateTime nextFinalizeAt,
        Integer finalizationAttempts
) {
}
