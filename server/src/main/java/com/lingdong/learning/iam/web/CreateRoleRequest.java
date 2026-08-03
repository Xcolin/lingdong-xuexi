package com.lingdong.learning.iam.web;

import com.lingdong.learning.iam.domain.RoleDataScope;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建自定义角色的 HTTP 请求。 */
public record CreateRoleRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 64) String name,
        @Size(max = 512) String description,
        @NotNull RoleDataScope dataScope
) { }
