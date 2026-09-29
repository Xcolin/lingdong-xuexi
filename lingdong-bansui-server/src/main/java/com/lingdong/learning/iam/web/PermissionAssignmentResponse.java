package com.lingdong.learning.iam.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.permission.application.PermissionAssignment;
import com.lingdong.learning.permission.domain.PermissionEffect;

/** 返回角色或用户保存的显式权限效果。 */
public record PermissionAssignmentResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long permissionId,
        PermissionEffect effect
) {
    static PermissionAssignmentResponse from(PermissionAssignment assignment) {
        return new PermissionAssignmentResponse(assignment.permissionId(), assignment.effect());
    }
}
