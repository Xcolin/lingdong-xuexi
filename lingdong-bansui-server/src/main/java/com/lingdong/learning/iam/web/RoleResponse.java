package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.domain.RoleType;

import java.time.LocalDateTime;

/** 角色目录响应，保证雪花主键以字符串传递给前端。 */
public record RoleResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        String name,
        RoleType type,
        RoleDataScope dataScope,
        boolean builtIn,
        RoleStatus status,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static RoleResponse from(Role role) {
        return new RoleResponse(role.id(), role.code(), role.name(), role.type(), role.dataScope(), role.builtIn(),
                role.status(), role.description(), role.createdAt(), role.updatedAt());
    }
}
