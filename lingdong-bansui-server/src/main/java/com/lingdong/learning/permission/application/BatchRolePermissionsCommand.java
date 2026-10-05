package com.lingdong.learning.permission.application;
import java.util.List;
public record BatchRolePermissionsCommand(Long operatorId, Long roleId, List<Long> permissionIds,
        List<Long> managedPermissionIds, List<PermissionAssignment> expectedAssignments) { }
