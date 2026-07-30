package com.lingdong.learning.datascope.application;

import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.OrganizationAdminMapper;
import com.lingdong.learning.datascope.infrastructure.persistence.RoleDataScopeMapper;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Configures explicit organization-based data boundaries under system-administrator control. */
@Service
public class DataScopeAdministrationService {
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;

    public DataScopeAdministrationService(UserRoleMapper userRoleMapper, UserMapper userMapper, OrganizationMapper organizationMapper,
                                          UserOrganizationMapper userOrganizationMapper, OrganizationAdminMapper organizationAdminMapper,
                                          RoleMapper roleMapper, RoleDataScopeMapper roleDataScopeMapper) {
        this.userRoleMapper = userRoleMapper; this.userMapper = userMapper; this.organizationMapper = organizationMapper;
        this.userOrganizationMapper = userOrganizationMapper; this.organizationAdminMapper = organizationAdminMapper;
        this.roleMapper = roleMapper; this.roleDataScopeMapper = roleDataScopeMapper;
    }

    @Transactional
    public void configureOrganizationAdministrator(Long operatorId, Long userId, Long organizationId) {
        requireSystemAdministrator(operatorId);
        if (userMapper.findById(userId) == null || organizationMapper.findById(organizationId) == null) throw new IllegalArgumentException("用户或组织不存在");
        if (!userOrganizationMapper.exists(userId, organizationId)) throw new IllegalStateException("组织管理员必须先关联对应组织");
        if (organizationAdminMapper.exists(userId, organizationId)) throw new IllegalStateException("用户已是该组织管理员");
        organizationAdminMapper.insert(userId, organizationId);
    }

    @Transactional
    public void configureRoleCustomScope(Long operatorId, Long roleId, Long organizationId) {
        requireSystemAdministrator(operatorId);
        var role = roleMapper.findById(roleId);
        if (role == null || role.dataScope() != RoleDataScope.CUSTOM || organizationMapper.findById(organizationId) == null) throw new IllegalArgumentException("角色或组织范围不合法");
        if (roleDataScopeMapper.exists(roleId, organizationId)) throw new IllegalStateException("角色已拥有该自定义组织范围");
        roleDataScopeMapper.insert(roleId, organizationId);
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")) throw new IllegalStateException("仅系统管理员可配置数据范围");
    }
}
