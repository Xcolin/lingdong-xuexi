package com.lingdong.learning.permission.application;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionResourceType;
public record CreatePermissionCommand(Long operatorId, String code, String name, PermissionResourceType resourceType, PermissionClient client, Long parentId) { }
