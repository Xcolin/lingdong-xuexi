package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;

import java.time.LocalDateTime;

/** 只包含最小支持信息的接口调用结果视图。 */
public record InterfaceServiceCallLogView(
        Long id,
        Long serviceId,
        String serviceName,
        String callerName,
        InterfaceCallResult result,
        String errorSummary,
        String traceId,
        LocalDateTime occurredAt
) { }
