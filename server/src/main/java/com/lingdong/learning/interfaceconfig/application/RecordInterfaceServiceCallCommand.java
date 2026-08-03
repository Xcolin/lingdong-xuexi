package com.lingdong.learning.interfaceconfig.application;

import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;

import java.time.LocalDateTime;

/** Internal adapter request to retain the minimal outcome of a permitted interface invocation. */
public record RecordInterfaceServiceCallCommand(
        Long serviceId,
        String callerName,
        InterfaceCallResult result,
        String errorSummary,
        String traceId,
        LocalDateTime occurredAt
) { }
