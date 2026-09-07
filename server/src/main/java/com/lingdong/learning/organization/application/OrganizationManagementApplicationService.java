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
 * <p>当前组织树尚未接入数据范围查询条件，因此所有全量组织管理操作仅允许系统管理员执行。</p>
 */
@Service
public class OrganizationManagementApplicationService {
    private static final String SYSTEM_ADMIN_ROLE = "SYS_ADMIN";

    private final UserRoleMapper userRoleMapper;
    private final OrganizationApplicationService organizationApplicationService;
    private final OrganizationQueryApplicationService organizationQueryApplicationService;
    private final FeatureAccessService featureAccessService;

    public OrganizationManagementApplicationService(
            UserRoleMapper userRoleMapper,
            OrganizationApplicationService organizationApplicationService,
            OrganizationQueryApplicationService organizationQueryApplicationService,
            FeatureAccessService featureAccessService
    ) {
        this.userRoleMapper = userRoleMapper;
        this.organizationApplicationService = organizationApplicationService;
        this.organizationQueryApplicationService = organizationQueryApplicationService;
        this.featureAccessService = featureAccessService;
    }

    public List<OrganizationType> listOrganizationTypes(Long operatorId) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationQueryApplicationService.listOrganizationTypes();
    }

    public OrganizationType createOrganizationType(Long operatorId, CreateOrganizationTypeCommand command) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.createOrganizationType(command);
    }

    public List<Organization> listOrganizations(Long operatorId) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationQueryApplicationService.listOrganizations();
    }

    public Organization createOrganization(Long operatorId, CreateOrganizationCommand command) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.createOrganization(command);
    }

    public Organization updateOrganization(Long operatorId, UpdateOrganizationCommand command) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.updateOrganization(operatorId, command);
    }

    public Organization enableOrganization(Long operatorId, Long organizationId, Integer expectedVersion) {
        requireFeatureEnabled();
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.enableOrganization(operatorId, organizationId, expectedVersion);
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, SYSTEM_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅系统管理员可管理组织");
        }
    }

    private void requireFeatureEnabled() {
        featureAccessService.requireEnabled("ORGANIZATION_MANAGEMENT", null);
    }
}
