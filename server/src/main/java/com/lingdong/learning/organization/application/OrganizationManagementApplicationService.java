package com.lingdong.learning.organization.application;

import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
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

    public OrganizationManagementApplicationService(
            UserRoleMapper userRoleMapper,
            OrganizationApplicationService organizationApplicationService,
            OrganizationQueryApplicationService organizationQueryApplicationService
    ) {
        this.userRoleMapper = userRoleMapper;
        this.organizationApplicationService = organizationApplicationService;
        this.organizationQueryApplicationService = organizationQueryApplicationService;
    }

    public List<OrganizationType> listOrganizationTypes(Long operatorId) {
        requireSystemAdministrator(operatorId);
        return organizationQueryApplicationService.listOrganizationTypes();
    }

    public OrganizationType createOrganizationType(Long operatorId, CreateOrganizationTypeCommand command) {
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.createOrganizationType(command);
    }

    public List<Organization> listOrganizations(Long operatorId) {
        requireSystemAdministrator(operatorId);
        return organizationQueryApplicationService.listOrganizations();
    }

    public Organization createOrganization(Long operatorId, CreateOrganizationCommand command) {
        requireSystemAdministrator(operatorId);
        return organizationApplicationService.createOrganization(command);
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, SYSTEM_ADMIN_ROLE)) {
            throw new SystemOperationAccessDeniedException("仅系统管理员可管理组织");
        }
    }
}
