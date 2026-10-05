package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.datascope.application.DataScopeAdministrationService;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.IamQueryApplicationService;
import com.lingdong.learning.iam.application.RoleApplicationService;
import com.lingdong.learning.menu.application.MenuGrantApplicationService;
import com.lingdong.learning.menu.application.MenuGrantViewNode;
import com.lingdong.learning.user.application.BatchAssignRoleToRoleCommand;
import com.lingdong.learning.user.application.BatchRoleAssignmentItem;
import com.lingdong.learning.user.application.UserAccessApplicationService;
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

/** 提供角色目录、创建与自定义数据范围配置接口。 */
@RestController
@RequestMapping("/api/v1/roles")
public class RoleManagementController {
    private final IamQueryApplicationService iamQueryApplicationService;
    private final RoleApplicationService roleApplicationService;
    private final DataScopeAdministrationService dataScopeAdministrationService;
    private final UserAccessApplicationService userAccessApplicationService;
    private final MenuGrantApplicationService menuGrantApplicationService;

    public RoleManagementController(
            IamQueryApplicationService iamQueryApplicationService,
            RoleApplicationService roleApplicationService,
            DataScopeAdministrationService dataScopeAdministrationService,
            UserAccessApplicationService userAccessApplicationService,
            MenuGrantApplicationService menuGrantApplicationService
    ) {
        this.iamQueryApplicationService = iamQueryApplicationService;
        this.roleApplicationService = roleApplicationService;
        this.dataScopeAdministrationService = dataScopeAdministrationService;
        this.userAccessApplicationService = userAccessApplicationService;
        this.menuGrantApplicationService = menuGrantApplicationService;
    }

    @RequirePermission("IAM_ROLE_READ")
    @GetMapping
    public List<RoleResponse> listRoles(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return iamQueryApplicationService.listRoles(currentUser.userId()).stream().map(RoleResponse::from).toList();
    }

    @RequirePermission("IAM_ROLE_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                   @Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                request.code(), request.name(), request.description(), request.dataScope(), currentUser.userId()
        )));
    }

    @RequirePermission("IAM_DATA_SCOPE_CONFIGURE")
    @PostMapping("/{roleId}/data-scopes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configureCustomDataScope(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @Valid @RequestBody ConfigureRoleDataScopeRequest request
    ) {
        dataScopeAdministrationService.configureRoleCustomScope(currentUser.userId(), roleId, request.organizationId());
    }

    /** 角色侧批量勾选用户授予：整批事务原子，任一失败整批回滚并报告失败用户及原因。 */
    @RequirePermission("IAM_USER_ROLE_ASSIGN")
    @PostMapping("/{roleId}/users:batch")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void batchAssignRoleToUsers(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @Valid @RequestBody BatchAssignRoleRequest request
    ) {
        userAccessApplicationService.batchAssignRole(new BatchAssignRoleToRoleCommand(
                currentUser.userId(), roleId,
                request.assignments().stream()
                        .map(item -> new BatchRoleAssignmentItem(item.userId(), item.organizationId()))
                        .toList()));
    }

    public record BatchRoleAssignmentItemRequest(Long userId, Long organizationId) { }
    public record BatchAssignRoleRequest(@jakarta.validation.constraints.NotEmpty List<BatchRoleAssignmentItemRequest> assignments) { }

    /** 菜单树授权视图：页面与权限按钮及其当前勾选状态，纯 UI 按钮与目录不出现。 */
    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @GetMapping("/{roleId}/menu-grants")
    public List<MenuGrantViewNode> menuGrants(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId
    ) {
        return menuGrantApplicationService.grantView(currentUser, roleId);
    }

    /** 菜单树勾选式授权差量保存：提交勾选编码集合，服务端差量增删菜单来源授权。 */
    @RequirePermission("IAM_ROLE_PERMISSION_GRANT")
    @PutMapping("/{roleId}/menu-grants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveMenuGrants(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable Long roleId,
            @RequestBody MenuGrantSaveRequest request
    ) {
        menuGrantApplicationService.saveGrants(currentUser, roleId, request.codes());
    }

    public record MenuGrantSaveRequest(List<String> codes) { }
}
