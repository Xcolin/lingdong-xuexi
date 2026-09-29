package com.lingdong.learning.permission.application;
import com.lingdong.learning.permission.domain.PermissionEffect;
public record ConfigureUserPermissionCommand(Long operatorId, Long userId, Long permissionId, PermissionEffect effect) { }
