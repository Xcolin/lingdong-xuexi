package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;

/** 为用户授予全局或指定组织范围角色的 HTTP 请求。 */
public record AssignUserRoleRequest(@NotNull Long roleId, Long organizationId) { }
