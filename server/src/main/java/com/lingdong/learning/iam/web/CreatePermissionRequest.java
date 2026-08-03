package com.lingdong.learning.iam.web;

import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建权限目录项的 HTTP 请求。 */
public record CreatePermissionRequest(
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 128) String name,
        @NotNull PermissionResourceType resourceType,
        @NotNull PermissionClient client,
        Long parentId
) { }
