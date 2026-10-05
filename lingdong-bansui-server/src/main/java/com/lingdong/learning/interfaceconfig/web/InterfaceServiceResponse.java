package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import com.lingdong.learning.interfaceconfig.domain.InterfaceService;
import com.lingdong.learning.interfaceconfig.domain.InterfaceServiceStatus;

import java.time.LocalDateTime;
import java.util.function.Function;

/** 生效接口服务响应，雪花标识始终按字符串传输；责任人展示为姓名。 */
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
    static InterfaceServiceResponse from(InterfaceService service, Function<Long, String> nameOf) {
        return new InterfaceServiceResponse(
                service.id().toString(), service.serviceName(), service.direction(), service.purpose(),
                service.callerName(), service.authorizationScope(), service.authorizationScopeValue(),
                nameOf.apply(service.ownerId()), service.status(), service.createdAt(), service.updatedAt()
        );
    }
}
