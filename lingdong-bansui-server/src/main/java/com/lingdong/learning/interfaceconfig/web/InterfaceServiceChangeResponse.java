package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.interfaceconfig.application.InterfaceServiceChangeView;
import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeType;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceChangeExecutionStatus;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;

import java.time.LocalDateTime;
import java.util.function.Function;

/** 接口服务变更与审核状态响应；责任人/申请人/审核人展示为姓名。 */
public record InterfaceServiceChangeResponse(
        String changeId,
        String taskId,
        String serviceId,
        InterfaceServiceChangeType changeType,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        String ownerId,
        InterfaceServiceStatus targetStatus,
        InterfaceServiceChangeExecutionStatus executionStatus,
        String failureReason,
        String taskTitle,
        String taskDescription,
        SystemTaskStatus taskStatus,
        String submittedBy,
        LocalDateTime submittedAt,
        String reviewedBy,
        LocalDateTime reviewedAt,
        String reviewComment,
        LocalDateTime createdAt
) {
    static InterfaceServiceChangeResponse from(InterfaceServiceChangeView view, Function<Long, String> nameOf) {
        return new InterfaceServiceChangeResponse(
                text(view.changeId()), text(view.taskId()), text(view.serviceId()), view.changeType(),
                view.serviceName(), view.direction(), view.purpose(), view.callerName(),
                view.authorizationScope(), view.authorizationScopeValue(), nameOf.apply(view.ownerId()), view.targetStatus(),
                view.executionStatus(), view.failureReason(),
                view.taskTitle(), view.taskDescription(), view.taskStatus(), nameOf.apply(view.submittedBy()),
                view.submittedAt(), nameOf.apply(view.reviewedBy()), view.reviewedAt(), view.reviewComment(), view.createdAt()
        );
    }

    private static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
