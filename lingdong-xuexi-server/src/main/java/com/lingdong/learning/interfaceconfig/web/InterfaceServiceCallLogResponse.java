package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.application.InterfaceServiceCallLogView;
import com.lingdong.learning.interfaceconfig.domain.InterfaceCallResult;

import java.time.LocalDateTime;

/** 最小化接口调用结果响应。 */
public record InterfaceServiceCallLogResponse(
        String id,
        String serviceId,
        String serviceName,
        String callerName,
        InterfaceCallResult result,
        String errorSummary,
        String traceId,
        LocalDateTime occurredAt
) {
    static InterfaceServiceCallLogResponse from(InterfaceServiceCallLogView view) {
        return new InterfaceServiceCallLogResponse(
                view.id().toString(), view.serviceId().toString(), view.serviceName(), view.callerName(),
                view.result(), view.errorSummary(), view.traceId(), view.occurredAt()
        );
    }
}
