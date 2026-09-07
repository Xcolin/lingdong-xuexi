package com.lingdong.learning.auth.web;

import com.lingdong.learning.auth.application.ParentAccountCancellationRecord;
import com.lingdong.learning.auth.application.ParentAccountCancellationStatus;

import java.time.LocalDateTime;

/** 注销申请响应，账号仍处于可登录的冷静期。 */
public record ParentAccountCancellationResponse(
        String cancellationId,
        ParentAccountCancellationStatus status,
        LocalDateTime requestedAt,
        LocalDateTime coolingEndsAt
) {
    public static ParentAccountCancellationResponse from(ParentAccountCancellationRecord record) {
        return new ParentAccountCancellationResponse(
                record.id().toString(), record.status(), record.requestedAt(), record.coolingEndsAt());
    }
}
