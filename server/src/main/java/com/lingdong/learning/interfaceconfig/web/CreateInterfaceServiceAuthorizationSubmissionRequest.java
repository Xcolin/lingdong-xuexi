package com.lingdong.learning.interfaceconfig.web;

import com.lingdong.learning.interfaceconfig.domain.InterfaceAuthorizationScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 接口服务授权范围变更并提交审核的请求。 */
public record CreateInterfaceServiceAuthorizationSubmissionRequest(
        @NotNull InterfaceAuthorizationScope authorizationScope,
        @Size(max = 128) String authorizationScopeValue,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description
) { }
