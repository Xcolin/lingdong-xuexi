package com.lingdong.learning.interfaceconfig.domain;

import java.time.LocalDateTime;

/** 已登记的接口服务元数据，明确排除凭证、地址及请求响应报文。 */
public record InterfaceService(
        Long id,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        Long ownerId,
        InterfaceServiceStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static InterfaceService enabled(Long id, InterfaceServiceChange change) {
        return new InterfaceService(
                id,
                change.serviceName(),
                change.direction(),
                change.purpose(),
                change.callerName(),
                change.authorizationScope(),
                change.authorizationScopeValue(),
                change.ownerId(),
                InterfaceServiceStatus.ENABLED,
                null,
                null
        );
    }
}
