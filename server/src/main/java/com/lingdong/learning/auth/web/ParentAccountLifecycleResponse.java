package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentAccountCancellationViewStatus;
import com.lingdong.learning.auth.application.ParentAccountLifecycleState;

import java.time.LocalDateTime;

/** 家长账号生命周期响应，雪花标识显式序列化为字符串。 */
public record ParentAccountLifecycleResponse(
        String maskedMobile,
        long activeStudentRelationshipCount,
        ParentAccountCancellationViewStatus cancellationStatus,
        String cancellationId,
        LocalDateTime requestedAt,
        LocalDateTime coolingEndsAt
) {
    public static ParentAccountLifecycleResponse from(ParentAccountLifecycleState state) {
        return new ParentAccountLifecycleResponse(
                state.maskedMobile(), state.activeStudentRelationshipCount(), state.cancellationStatus(),
                state.cancellationId() == null ? null : state.cancellationId().toString(),
                state.requestedAt(), state.coolingEndsAt());
    }
}
