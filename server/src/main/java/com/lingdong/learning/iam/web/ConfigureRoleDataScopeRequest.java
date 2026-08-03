package com.lingdong.learning.iam.web;

import jakarta.validation.constraints.NotNull;

/** 为 CUSTOM 数据范围角色增加一个组织范围。 */
public record ConfigureRoleDataScopeRequest(@NotNull Long organizationId) { }
