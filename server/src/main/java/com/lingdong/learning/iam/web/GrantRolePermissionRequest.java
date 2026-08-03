package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;

/** 向角色授予单个权限目录项的 HTTP 请求。 */
public record GrantRolePermissionRequest(@NotNull Long permissionId) { }
