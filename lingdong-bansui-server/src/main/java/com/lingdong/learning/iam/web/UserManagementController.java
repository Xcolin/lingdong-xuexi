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
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.application.UserRoleAssociationService roleAssociation;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.application.UserOrganizationEditService organizationEdit;
    @org.springframework.beans.factory.annotation.Autowired private com.lingdong.learning.user.application.AdminUserEmailService emails;
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

    @RequirePermission("IAM_USER_CREATE")
    @GetMapping("/creation-organizations")
    public java.util.List<com.lingdong.learning.user.application.UserAccessApplicationService.CreationOrganizationOption> creationOrganizations(
            @AuthenticationPrincipal AuthenticatedUser actor,@RequestParam(required=false)UserType type){
        if(actor.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB)throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("用户创建需要Web会话");
        return userAccessApplicationService.creationOrganizations(actor.userId(),type);
    }

    @GetMapping("/scope-organizations")
    public java.util.List<com.lingdong.learning.user.application.UserAccessApplicationService.CreationOrganizationOption> scopeOrganizations(
            @AuthenticationPrincipal AuthenticatedUser actor,@RequestParam String permission){
        if(actor==null || (actor.clientType()!=com.lingdong.learning.auth.domain.AuthClientType.WEB
                && !(actor.clientType()==com.lingdong.learning.auth.domain.AuthClientType.MINIAPP
                    && java.util.Set.of("ORG_MEMBER_ASSOCIATE","ORG_ADMIN_ASSIGN").contains(permission))))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前会话无权选择组织");
        return userAccessApplicationService.scopeOrganizations(actor.userId(),permission);
    }

    @RequirePermission("IAM_USER_READ")
    @GetMapping("/{id}")
    public UserResponse findUser(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long id) {
        var user=iamQueryApplicationService.findUser(currentUser.userId(), id);
        return UserResponse.from(user).withEmail(emails.findForUsers(java.util.List.of(id)).get(id));
    }

    @RequirePermission("IAM_USER_LIST")
    @GetMapping
    public UserDirectoryPageResponse listUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserType type,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String identityRoleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        var result=UserDirectoryPageResponse.from(iamQueryApplicationService.listUsers(currentUser.userId(), keyword, type, status, organizationId, page, pageSize,identityRoleCode));
        var contacts=emails.findForUsers(result.items().stream().map(UserResponse::id).toList());
        return new UserDirectoryPageResponse(result.items().stream().map(u->u.withEmail(contacts.get(u.id()))).toList(),result.page(),result.pageSize(),result.total());
    }

    @RequirePermission("IAM_USER_CREATE")
    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    @ResponseStatus(HttpStatus.CREATED)
    public ManagedUserCreationResponse createUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   @Valid @RequestBody CreateUserRequest request) {
        var result=userAccessApplicationService.createManagedUserResult(new CreateUserCommand(
                request.username(), request.displayName(), request.mobile(), request.type(), currentUser.userId()
        ), request.organizationId(), request.password(), request.studentSchoolOrganizationId(), request.gradeCode(), request.defaultRoleCode());
        emails.initializeCreatedUser(currentUser.userId(),result.user().id(),request.organizationId(),request.email());
        var response=ManagedUserCreationResponse.from(result);
        return new ManagedUserCreationResponse(response.user().withEmail(emails.findForUsers(java.util.List.of(result.user().id())).get(result.user().id())),response.studentId(),response.initialLoginCode());
    }

    @RequirePermission("IAM_USER_CREATE")
    @PostMapping("/{id}/student-profile")
    public ManagedUserCreationResponse completeStudentProfile(@AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id, @Valid @RequestBody CompleteStudentProfileRequest request) {
        return ManagedUserCreationResponse.from(userAccessApplicationService.completeStudentProfileResult(currentUser.userId(), id,
                request.organizationId(), request.studentSchoolOrganizationId(), request.gradeCode()));
    }

    @RequirePermission("IAM_USER_UPDATE")
    @GetMapping("/{id}/organization-edit")
    public com.lingdong.learning.user.application.UserOrganizationEditService.Context organizationEdit(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id) {
        return organizationEdit.context(actor.userId(),id);
    }

    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @GetMapping("/{id}/role-associations")
    public com.lingdong.learning.user.application.UserRoleAssociationService.Context roleAssociations(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id){return roleAssociation.context(actor.userId(),id);}
    public record UserRoleItemsRequest(@jakarta.validation.constraints.NotEmpty java.util.List<com.lingdong.learning.user.application.UserRoleAssociationService.Item> assignments){}
    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @PostMapping("/{id}/roles:batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addUserRoles(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody UserRoleItemsRequest request){roleAssociation.change(actor.userId(),id,request.assignments(),false);}
    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @PostMapping("/{id}/roles:revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeUserRoles(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody UserRoleItemsRequest request){roleAssociation.change(actor.userId(),id,request.assignments(),true);}

    public record UpdateUserProfileRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=64) String displayName,@jakarta.validation.constraints.Size(max=32) String mobile,com.fasterxml.jackson.databind.JsonNode email,@jakarta.validation.constraints.NotEmpty java.util.List<@jakarta.validation.constraints.NotNull Long> organizationIds,@jakarta.validation.constraints.NotNull java.util.List<@jakarta.validation.constraints.NotNull Long> expectedOrganizationIds) { }
    @RequirePermission("IAM_USER_UPDATE")
    @org.springframework.transaction.annotation.Transactional
    @PatchMapping("/{id}")
    public UserResponse updateProfile(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable Long id,@Valid @RequestBody UpdateUserProfileRequest request) {
        emails.maintain(actor.userId(),id,request.email());
        var user=organizationEdit.update(actor.userId(),id,request.displayName(),request.mobile(),request.organizationIds(),request.expectedOrganizationIds());
        return UserResponse.from(user).withEmail(emails.findForUsers(java.util.List.of(id)).get(id));
    }

    @RequirePermission("IAM_USER_STATUS_CHANGE")
    @PatchMapping("/{id}/status")
    public UserResponse updateUserStatus(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                         @PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        return UserResponse.from(userAccessApplicationService.updateAuthorizedStatus(
                new UpdateUserStatusCommand(id, request.status(), currentUser.userId())));
    }

    @RequirePermission("ORG_MEMBER_ASSOCIATE")
    @PostMapping("/{id}/organizations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void associateUserWithOrganization(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long id, @Valid @RequestBody AssociateUserOrganizationRequest request
    ) {
        userAccessApplicationService.associateAuthorizedOrganization(
                new AssociateUserWithOrganizationCommand(id, request.organizationId(), currentUser.userId())
        );
    }

    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @PostMapping("/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignRoleToUser(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                 @PathVariable Long id, @Valid @RequestBody AssignUserRoleRequest request) {
        userAccessApplicationService.assignAuthorizedRole(new AssignRoleToUserCommand(
                id, request.roleId(), request.organizationId(), currentUser.userId()));
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
