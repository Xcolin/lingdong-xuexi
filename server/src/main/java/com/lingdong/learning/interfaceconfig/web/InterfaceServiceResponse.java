package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;

import java.time.LocalDateTime;

/** 生效接口服务响应，雪花标识始终按字符串传输。 */
public record InterfaceServiceResponse(
        String id,
        String serviceName,
        InterfaceDirection direction,
        InterfacePurpose purpose,
        String callerName,
        InterfaceAuthorizationScope authorizationScope,
        String authorizationScopeValue,
        String ownerId,
        InterfaceServiceStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static InterfaceServiceResponse from(InterfaceService service) {
        return new InterfaceServiceResponse(
                service.id().toString(), service.serviceName(), service.direction(), service.purpose(),
                service.callerName(), service.authorizationScope(), service.authorizationScopeValue(),
                service.ownerId().toString(), service.status(), service.createdAt(), service.updatedAt()
        );
    }
}
