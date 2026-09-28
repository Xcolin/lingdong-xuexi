package com.lingdong.learning.interfaceconfig.domain;

import java.time.LocalDateTime;

/** 保存不可变变更快照，避免任务提交后被后续编辑改变执行含义。 */
public record InterfaceServiceChange(
        Long id,
        Long taskId,
        Long serviceId,
        InterfaceServiceChangeType changeType,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        Long ownerId,
        InterfaceServiceStatus targetStatus,
        InterfaceServiceChangeExecutionStatus executionStatus,
        String failureReason,
        InterfaceServiceStatus beforeStatus,
        InterfaceAuthorizationScope beforeAuthorizationScope,
        String beforeAuthorizationScopeValue,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InterfaceServiceChange create(
            Long id,
            Long taskId,
            String serviceName,
            InterfaceDirection direction,
            InterfacePurpose purpose,
            String callerName,
            InterfaceAuthorizationScope authorizationScope,
            String authorizationScopeValue,
            Long ownerId
    ) {
        return new InterfaceServiceChange(
                id, taskId, null, InterfaceServiceChangeType.CREATE, serviceName, direction, purpose, callerName,
                authorizationScope, authorizationScopeValue, ownerId, InterfaceServiceStatus.ENABLED,
                InterfaceServiceChangeExecutionStatus.PENDING, null, null, null, null, null, null
        );
    }

    public static InterfaceServiceChange disable(Long id, Long taskId, Long serviceId) {
        return new InterfaceServiceChange(
                id, taskId, serviceId, InterfaceServiceChangeType.DISABLE, null, null, null, null,
                null, null, null, InterfaceServiceStatus.DISABLED,
                InterfaceServiceChangeExecutionStatus.PENDING, null, null, null, null, null, null
        );
    }

    public static InterfaceServiceChange enable(Long id, Long taskId, Long serviceId) {
        return new InterfaceServiceChange(
                id, taskId, serviceId, InterfaceServiceChangeType.ENABLE, null, null, null, null,
                null, null, null, InterfaceServiceStatus.ENABLED,
                InterfaceServiceChangeExecutionStatus.PENDING, null, null, null, null, null, null
        );
    }

    public static InterfaceServiceChange changeAuthorization(
            Long id,
            Long taskId,
            Long serviceId,
            InterfaceAuthorizationScope authorizationScope,
            String authorizationScopeValue
    ) {
        return new InterfaceServiceChange(
                id, taskId, serviceId, InterfaceServiceChangeType.CHANGE_AUTHORIZATION, null, null, null, null,
                authorizationScope, authorizationScopeValue, null, null,
                InterfaceServiceChangeExecutionStatus.PENDING, null, null, null, null, null, null
        );
    }

    /** 在创建申请时固化来源，不依赖服务后续状态。 */
    public InterfaceServiceChange withBefore(InterfaceService service) {
        return new InterfaceServiceChange(id, taskId, serviceId, changeType, service.serviceName(),
                service.direction(), service.purpose(), service.callerName(), authorizationScope,
                authorizationScopeValue, service.ownerId(), targetStatus, executionStatus, failureReason,
                service.status(), service.authorizationScope(), service.authorizationScopeValue(), createdAt, updatedAt);
    }
}
