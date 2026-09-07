package com.lingdong.learning.auth.application;

import java.time.LocalDateTime;

/** 家长账号生命周期状态，手机号只返回掩码。 */
public record ParentAccountLifecycleState(
        String maskedMobile,
        long activeStudentRelationshipCount,
        ParentAccountCancellationViewStatus cancellationStatus,
        Long cancellationId,
        LocalDateTime requestedAt,
        LocalDateTime coolingEndsAt
) {
}
