package com.lingdong.learning.interfaceconfig.domain;

import java.time.LocalDateTime;

/**
 * 隐私安全的调用结果。模型不保存请求正文、响应正文、凭据或位置信息。
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
