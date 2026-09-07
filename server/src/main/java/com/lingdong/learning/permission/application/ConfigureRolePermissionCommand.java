package com.lingdong.learning.permission.application;

import com.lingdong.learning.permission.domain.PermissionEffect;

/** 配置角色对单项权限的明确允许或明确禁止。 */
public record ConfigureRolePermissionCommand(
        Long operatorId,
        Long roleId,
        Long permissionId,
        PermissionEffect effect
) {
}
