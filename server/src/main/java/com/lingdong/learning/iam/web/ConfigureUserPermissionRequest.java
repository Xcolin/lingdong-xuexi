package com.lingdong.learning.iam.web;

import com.lingdong.learning.permission.domain.PermissionEffect;
import jakarta.validation.constraints.NotNull;

/** 配置用户对单项权限的显式允许或拒绝。 */
public record ConfigureUserPermissionRequest(@NotNull PermissionEffect effect) { }
