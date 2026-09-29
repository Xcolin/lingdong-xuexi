package com.lingdong.learning.organization.application;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.domain.AuthClientType;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.stereotype.Service;

import java.util.List;

/** 查询当前机构管理员小程序工作台，不接受外部用户或组织范围参数。 */
@Service
public class OrganizationWorkbenchQueryService {
    private static final String ORGANIZATION_ADMIN_ROLE = "ORG_ADMIN";
    private static final String ORGANIZATION_MINIAPP_AUTH_FEATURE = "ORGANIZATION_MINIAPP_AUTH";

    private final UserMapper userMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final FeatureAccessService featureAccessService;
    private final PermissionDecisionService permissionDecisionService;

    public OrganizationWorkbenchQueryService(
            UserMapper userMapper,
            OrganizationAdminMapper organizationAdminMapper,
            FeatureAccessService featureAccessService,
            PermissionDecisionService permissionDecisionService
    ) {
        this.userMapper = userMapper;
        this.organizationAdminMapper = organizationAdminMapper;
        this.featureAccessService = featureAccessService;
        this.permissionDecisionService = permissionDecisionService;
    }

    /** 返回当前机构管理员直接管理的启用组织摘要。 */
    public OrganizationWorkbenchContext getContext(AuthenticatedUser currentUser) {
        featureAccessService.requireEnabled(ORGANIZATION_MINIAPP_AUTH_FEATURE, null);
        if (currentUser == null
                || currentUser.clientType() != AuthClientType.MINIAPP
                || !currentUser.roleCodes().contains(ORGANIZATION_ADMIN_ROLE)) {
            throw accessDenied();
        }
        User user = userMapper.findById(currentUser.userId());
        if (user == null || user.status() != UserStatus.ENABLED || user.type() != UserType.ORGANIZATION) {
            throw accessDenied();
        }
        List<OrganizationWorkbenchOrganization> organizations = organizationAdminMapper
                .findEnabledManagedOrganizationSummaries(user.id()).stream()
                .map(row -> new OrganizationWorkbenchOrganization(row.id(), row.name(), row.typeCode()))
                .toList();
        if (organizations.isEmpty()) {
            throw accessDenied();
        }
        return new OrganizationWorkbenchContext(
                user.id(), user.username(), user.displayName(),
                permissionDecisionService.findAllowedCodes(user.id(), PermissionClient.MINIAPP),
                organizations);
    }

    private SystemOperationAccessDeniedException accessDenied() {
        return new SystemOperationAccessDeniedException("当前身份不能访问机构工作台");
    }
}
