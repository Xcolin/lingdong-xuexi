package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import com.lingdong.learning.interfaceconfig.domain.InterfaceDirection;
import com.lingdong.learning.interfaceconfig.domain.InterfacePurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 接口服务登记并提交审核的请求。 */
public record CreateInterfaceServiceRegistrationRequest(
        @NotBlank @Size(max = 100) String serviceName,
        @NotNull InterfaceDirection direction,
        @NotNull InterfacePurpose purpose,
        @NotBlank @Size(max = 100) String callerName,
        @NotNull InterfaceAuthorizationScope authorizationScope,
        @Size(max = 128) String authorizationScopeValue,
        @NotNull Long ownerId,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description
) { }
