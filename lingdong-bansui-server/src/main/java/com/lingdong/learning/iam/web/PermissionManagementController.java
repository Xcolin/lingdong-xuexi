package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.iam.application.IamQueryApplicationService;
import com.lingdong.learning.permission.application.ConfigureUserPermissionCommand;
import com.lingdong.learning.permission.application.ConfigureRolePermissionCommand;
import com.lingdong.learning.permission.application.GrantRolePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import com.lingdong.learning.permission.application.BatchRolePermissionsCommand;
import com.lingdong.learning.permission.application.PermissionAssignment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 提供权限目录、角色授权和用户显式权限配置接口。 */
@RestController
@RequestMapping("/api/v1")
public class PermissionManagementController {
    private final IamQueryApplicationService iamQueryApplicationService;
    private final PermissionAdministrationService permissionAdministrationService;

    public PermissionManagementController(
            IamQueryApplicationService iamQueryApplicationService,
            PermissionAdministrationService permissionAdministrationService
    ) {
        this.iamQueryApplicationService = iamQueryApplicationService;
        this.permissionAdministrationService = permissionAdministrationService;
    }

    @RequirePermission("IAM_PERMISSION_READ")
    @GetMapping("/permissions")
    public List<PermissionResponse> listPermissions(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return iamQueryApplicationService.listPermissions(currentUser.userId()).stream().map(PermissionResponse::from).toList();
    }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @GetMapping("/iam/role-permission-menus")
    public List<com.lingdong.learning.menu.domain.MenuNode> listRolePermissionMenus(
            @AuthenticationPrincipal AuthenticatedUser currentUser) {
        return permissionAdministrationService.listRolePermissionMenus(currentUser.userId());
    }

    /** 已关闭的独立权限目录写入口；权限资源统一通过菜单管理维护。 */
    @PostMapping("/permissions")
    public void rejectStandalonePermissionCreation() {
        throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("权限资源请通过菜单管理维护");
    }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @PostMapping("/roles/{roleId}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void grantRolePermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @Valid @RequestBody GrantRolePermissionRequest request
    ) {
        permissionAdministrationService.grantRolePermission(new GrantRolePermissionCommand(
                currentUser.userId(), roleId, request.permissionId()
        ));
    }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @GetMapping("/roles/{roleId}/permissions")
    public List<PermissionAssignmentResponse> listRolePermissions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId
    ) {
        return permissionAdministrationService.listRolePermissions(currentUser.userId(), roleId)
                .stream().map(PermissionAssignmentResponse::from).toList();
    }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @PutMapping("/roles/{roleId}/permissions:batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batchRolePermissions(@AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId, @Valid @RequestBody BatchRolePermissionsRequest request) {
        permissionAdministrationService.batchRolePermissions(new BatchRolePermissionsCommand(
                currentUser.userId(), roleId, request.permissionIds(), request.managedPermissionIds(), request.expectedAssignments()));
    }

    public record BatchRolePermissionsRequest(
            @jakarta.validation.constraints.NotNull List<Long> permissionIds,
            @jakarta.validation.constraints.NotNull List<Long> managedPermissionIds,
            @jakarta.validation.constraints.NotNull List<PermissionAssignment> expectedAssignments) { }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @PutMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configureRolePermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @PathVariable Long permissionId,
            @Valid @RequestBody ConfigureUserPermissionRequest request
    ) {
        permissionAdministrationService.configureRolePermission(new ConfigureRolePermissionCommand(
                currentUser.userId(), roleId, permissionId, request.effect()));
    }

    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @DeleteMapping("/roles/{roleId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeRolePermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @PathVariable Long permissionId
    ) {
        permissionAdministrationService.removeRolePermission(
                currentUser.userId(), roleId, permissionId);
    }

    @RequirePermission("IAM_USER_PERMISSION_CONFIGURE")
    @GetMapping("/users/{userId}/permission-tree")
    public com.lingdong.learning.permission.application.UserPermissionTree userTree(
            @AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long userId) {
        return permissionAdministrationService.userPermissionTree(currentUser.userId(), userId);
    }

    @RequirePermission("IAM_USER_PERMISSION_CONFIGURE")
    @PutMapping("/users/{userId}/permissions:batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batchUserPermissions(@AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId, @Valid @RequestBody BatchRolePermissionsRequest request) {
        permissionAdministrationService.batchUserPermissions(new com.lingdong.learning.permission.application.BatchUserPermissionsCommand(
                currentUser.userId(), userId, request.permissionIds(), request.managedPermissionIds(), request.expectedAssignments()));
    }

    @RequirePermission("IAM_USER_PERMISSION_CONFIGURE")
    @PutMapping("/users/{userId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configureUserPermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId,
            @PathVariable Long permissionId,
            @Valid @RequestBody ConfigureUserPermissionRequest request
    ) {
        permissionAdministrationService.configureUserPermission(new ConfigureUserPermissionCommand(
                currentUser.userId(), userId, permissionId, request.effect()
        ));
    }

    @RequirePermission("IAM_USER_PERMISSION_CONFIGURE")
    @GetMapping("/users/{userId}/permissions")
    public List<PermissionAssignmentResponse> listUserPermissions(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId
    ) {
        return permissionAdministrationService.listUserPermissions(currentUser.userId(), userId)
                .stream().map(PermissionAssignmentResponse::from).toList();
    }

    @RequirePermission("IAM_USER_PERMISSION_CONFIGURE")
    @DeleteMapping("/users/{userId}/permissions/{permissionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeUserPermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long userId,
            @PathVariable Long permissionId
    ) {
        permissionAdministrationService.removeUserPermission(
                currentUser.userId(), userId, permissionId);
    }
}
