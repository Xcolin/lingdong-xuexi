package com.lingdong.learning.permission.application;

import com.lingdong.learning.permission.domain.PermissionEffect;

/** 角色或用户对单项权限保存的显式效果。 */
public record PermissionAssignment(Long permissionId, PermissionEffect effect) {
}
