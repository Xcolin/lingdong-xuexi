package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.feature.application.FeatureAccessService;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationType;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组织管理 HTTP 用例边界。
 *
 * <p>操作权限与组织数据范围实时解析，根节点和全局类型配置需要全局数据范围。</p>
 */
@Service
public class OrganizationManagementApplicationService {
    private final com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes;
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";

    private final UserRoleMapper userRoleMapper;
    private final OrganizationApplicationService organizationApplicationService;
    private final OrganizationQueryApplicationService organizationQueryApplicationService;
    private final FeatureAccessService featureAccessService;

    public OrganizationManagementApplicationService(
            UserRoleMapper userRoleMapper,
            OrganizationApplicationService organizationApplicationService,
            OrganizationQueryApplicationService organizationQueryApplicationService,
            FeatureAccessService featureAccessService, com.lingdong.learning.permission.application.PermissionDecisionService decisions, com.lingdong.learning.datascope.application.OrganizationDataScopeService scopes) {
        this.scopes = scopes;
        this.decisions = decisions;
        this.userRoleMapper = userRoleMapper;
        this.organizationApplicationService = organizationApplicationService;
        this.organizationQueryApplicationService = organizationQueryApplicationService;
        this.featureAccessService = featureAccessService;
    }

    public List<OrganizationType> listOrganizationTypes(Long operatorId) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_TYPE_READ");
        return organizationQueryApplicationService.listOrganizationTypes();
    }

    public OrganizationType createOrganizationType(Long operatorId, CreateOrganizationTypeCommand command) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_TYPE_CREATE");
        requireGlobalScope(operatorId);
        return organizationApplicationService.createOrganizationType(command);
    }

    public List<Organization> listOrganizations(Long operatorId) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_NODE_READ");
        return scopes.findAccessibleOrganizations(operatorId);
    }

    public Organization createOrganization(Long operatorId, CreateOrganizationCommand command) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_NODE_CREATE");
        requireScope(operatorId, command.parentId());
        return organizationApplicationService.createOrganization(command);
    }

    public Organization updateOrganization(Long operatorId, UpdateOrganizationCommand command) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_NODE_UPDATE");
        requireScope(operatorId, command.organizationId());
        return organizationApplicationService.updateOrganization(operatorId, command);
    }

    public Organization enableOrganization(Long operatorId, Long organizationId, Integer expectedVersion) {
        requireFeatureEnabled();
        requirePermission(operatorId, "ORG_NODE_UPDATE");
        requireScope(operatorId, organizationId);
        return organizationApplicationService.enableOrganization(operatorId, organizationId, expectedVersion);
    }

    private void requirePermission(Long operatorId, String code) {
        if (operatorId == null || !decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, code)) {
            throw new SystemOperationAccessDeniedException("当前账号无管理组织权限");
        }
    }

    private void requireGlobalScope(Long operatorId) {
        if (!scopes.resolve(operatorId).allOrganizations()) throw new SystemOperationAccessDeniedException("组织类型管理需要全局数据范围");
    }
    private void requireScope(Long operatorId, Long organizationId) {
        if (scopes.resolve(operatorId).allOrganizations()) return;
        if (organizationId == null) requireGlobalScope(operatorId);
        else if (!scopes.canAccess(operatorId, organizationId)) throw new SystemOperationAccessDeniedException("组织不在可管理数据范围内");
    }
    private void requireFeatureEnabled() {
        featureAccessService.requireEnabled("ORGANIZATION_MANAGEMENT", null);
    }
}
