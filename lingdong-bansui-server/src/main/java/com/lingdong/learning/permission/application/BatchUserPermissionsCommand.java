package com.lingdong.learning.permission.application;
import java.util.List;
public record BatchUserPermissionsCommand(Long operatorId, Long userId, List<Long> permissionIds,
 List<Long> managedPermissionIds, List<PermissionAssignment> expectedAssignments) { }
