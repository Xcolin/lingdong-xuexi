package com.lingdong.learning.interfaceconfig.domain;

import java.time.LocalDateTime;

/**
 * Privacy-safe call outcome. The model intentionally carries no request body, response body, credential, or location.
 */
public record InterfaceServiceCallLog(
        Long id,
        Long serviceId,
        String callerName,
        InterfaceCallResult result,
        String errorSummary,
        String traceId,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {
    public static InterfaceServiceCallLog create(
            Long id,
            Long serviceId,
            String callerName,
            InterfaceCallResult result,
            String errorSummary,
            String traceId,
            LocalDateTime occurredAt
    ) {
        return new InterfaceServiceCallLog(
                id, serviceId, callerName, result, errorSummary, traceId, occurredAt, null
        );
    }
}
