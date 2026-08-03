package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.datascope.application.DataScopeAdministrationService;
import com.lingdong.learning.iam.application.CreateCustomRoleCommand;
import com.lingdong.learning.iam.application.IamQueryApplicationService;
import com.lingdong.learning.iam.application.RoleApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public RoleManagementController(
            IamQueryApplicationService iamQueryApplicationService,
            RoleApplicationService roleApplicationService,
            DataScopeAdministrationService dataScopeAdministrationService
    ) {
        this.iamQueryApplicationService = iamQueryApplicationService;
        this.roleApplicationService = roleApplicationService;
        this.dataScopeAdministrationService = dataScopeAdministrationService;
    }

    @RequirePermission("IAM_ROLE_READ")
    @GetMapping
    public List<RoleResponse> listRoles() {
        return iamQueryApplicationService.listRoles().stream().map(RoleResponse::from).toList();
    }

    @RequirePermission("IAM_ROLE_CREATE")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleResponse createRole(@Valid @RequestBody CreateRoleRequest request) {
        return RoleResponse.from(roleApplicationService.createCustomRole(new CreateCustomRoleCommand(
                request.code(), request.name(), request.description(), request.dataScope()
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
}
