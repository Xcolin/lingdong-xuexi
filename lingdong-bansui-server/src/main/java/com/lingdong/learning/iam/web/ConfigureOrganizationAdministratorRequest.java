package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;

/** 配置组织管理员时提交的用户与组织标识。 */
public record ConfigureOrganizationAdministratorRequest(
        @NotNull Long userId,
        @NotNull Long organizationId
) { }
