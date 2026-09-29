package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionResourceType;
import com.lingdong.learning.permission.domain.PermissionStatus;

/** 权限目录响应，所有标识字段均作为字符串交付前端。 */
public record PermissionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        String name,
        PermissionResourceType resourceType,
        PermissionClient client,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        PermissionStatus status,
        String description
) {
    static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.id(), permission.code(), permission.name(), permission.resourceType(),
                permission.client(), permission.parentId(), permission.status(), permission.description());
    }
}
