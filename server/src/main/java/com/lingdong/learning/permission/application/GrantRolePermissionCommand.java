package com.lingdong.learning.permission.application;
public record GrantRolePermissionCommand(Long operatorId, Long roleId, Long permissionId) { }
