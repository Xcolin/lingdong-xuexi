package com.lingdong.learning.iam.web;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.common.security.RequirePermission;
import com.lingdong.learning.datascope.application.DataScopeAdministrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 提供组织管理员的数据范围配置入口。 */
@RestController
@RequestMapping("/api/v1/organization-admins")
public class DataScopeManagementController {
    private final DataScopeAdministrationService dataScopeAdministrationService;

    public DataScopeManagementController(DataScopeAdministrationService dataScopeAdministrationService) {
        this.dataScopeAdministrationService = dataScopeAdministrationService;
    }

    @RequirePermission("IAM_DATA_SCOPE_CONFIGURE")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void configureOrganizationAdministrator(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ConfigureOrganizationAdministratorRequest request
    ) {
        dataScopeAdministrationService.configureOrganizationAdministrator(
                currentUser.userId(), request.userId(), request.organizationId()
        );
    }
}
