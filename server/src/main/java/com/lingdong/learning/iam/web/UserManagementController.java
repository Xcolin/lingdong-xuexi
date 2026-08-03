package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.auth.application.SetPlatformUserPasswordCommand;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.iam.application.IamQueryApplicationService;
import com.lingdong.learning.user.application.AssignRoleToUserCommand;
import com.lingdong.learning.user.application.AssociateUserWithOrganizationCommand;
import com.lingdong.learning.user.application.CreateUserCommand;
import com.lingdong.learning.user.application.UpdateUserStatusCommand;
import com.lingdong.learning.user.application.UserAccessApplicationService;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 提供受 IAM 权限保护的用户管理查询接口。 */
@RestController
@RequestMapping("/api/v1/users")
public class UserManagementController {
    private final IamQueryApplicationService iamQueryApplicationService;
    private final UserAccessApplicationService userAccessApplicationService;
    private final AuthenticationApplicationService authenticationApplicationService;

    public UserManagementController(
            IamQueryApplicationService iamQueryApplicationService,
            UserAccessApplicationService userAccessApplicationService,
            AuthenticationApplicationService authenticationApplicationService
    ) {
        this.iamQueryApplicationService = iamQueryApplicationService;
        this.userAccessApplicationService = userAccessApplicationService;
        this.authenticationApplicationService = authenticationApplicationService;
    }

    @RequirePermission("IAM_USER_READ")
    @GetMapping("/{id}")
    public UserResponse findUser(@PathVariable Long id) {
        return UserResponse.from(iamQueryApplicationService.findUser(id));
    }

    @RequirePermission("IAM_USER_LIST")
    @GetMapping
    public UserDirectoryPageResponse listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserType type,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return UserDirectoryPageResponse.from(iamQueryApplicationService.listUsers(keyword, type, status, page, pageSize));
    }

    @RequirePermission("IAM_USER_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(userAccessApplicationService.createUser(new CreateUserCommand(
                request.username(), request.displayName(), request.mobile(), request.type()
        )));
    }

    @RequirePermission("IAM_USER_STATUS_CHANGE")
    @PatchMapping("/{id}/status")
    public UserResponse updateUserStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return UserResponse.from(userAccessApplicationService.updateStatus(new UpdateUserStatusCommand(id, request.status())));
    }

    @RequirePermission("IAM_USER_ORGANIZATION_ASSIGN")
    @PostMapping("/{id}/organizations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void associateUserWithOrganization(
            @PathVariable Long id, @Valid @RequestBody AssociateUserOrganizationRequest request
    ) {
        userAccessApplicationService.associateWithOrganization(
                new AssociateUserWithOrganizationCommand(id, request.organizationId())
        );
    }

    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRoleToUser(@PathVariable Long id, @Valid @RequestBody AssignUserRoleRequest request) {
        userAccessApplicationService.assignRole(new AssignRoleToUserCommand(id, request.roleId(), request.organizationId()));
    }

    @RequirePermission("IAM_USER_PASSWORD_SET")
    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setPlatformUserPassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id,
            @Valid @RequestBody SetUserPasswordRequest request
    ) {
        authenticationApplicationService.setPlatformUserPassword(
                new SetPlatformUserPasswordCommand(currentUser.userId(), id, request.password())
        );
    }
}
