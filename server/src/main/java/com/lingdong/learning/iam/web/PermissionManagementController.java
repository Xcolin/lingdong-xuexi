package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.iam.application.IamQueryApplicationService;
import com.lingdong.learning.permission.application.ConfigureUserPermissionCommand;
import com.lingdong.learning.permission.application.CreatePermissionCommand;
import com.lingdong.learning.permission.application.GrantRolePermissionCommand;
import com.lingdong.learning.permission.application.PermissionAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
    public List<PermissionResponse> listPermissions() {
        return iamQueryApplicationService.listPermissions().stream().map(PermissionResponse::from).toList();
    }

    @RequirePermission("IAM_PERMISSION_CREATE")
    @PostMapping("/permissions")
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionResponse createPermission(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreatePermissionRequest request
    ) {
        return PermissionResponse.from(permissionAdministrationService.createPermission(new CreatePermissionCommand(
                currentUser.userId(), request.code(), request.name(), request.resourceType(), request.client(), request.parentId()
        )));
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
}
