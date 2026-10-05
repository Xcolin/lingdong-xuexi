package com.lingdong.learning.datascope.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
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
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final OrganizationAdminMapper organizationAdminMapper;
    private final RoleMapper roleMapper;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final IdGenerator idGenerator;
    private final IamChangeAuditService auditService;

    public DataScopeAdministrationService(UserRoleMapper userRoleMapper, UserMapper userMapper, OrganizationMapper organizationMapper,
                                          UserOrganizationMapper userOrganizationMapper, OrganizationAdminMapper organizationAdminMapper,
                                          RoleMapper roleMapper, RoleDataScopeMapper roleDataScopeMapper, IdGenerator idGenerator,
                                          IamChangeAuditService auditService, com.lingdong.learning.permission.application.PermissionDecisionService decisions) {
        this.decisions = decisions;
        this.userRoleMapper = userRoleMapper; this.userMapper = userMapper; this.organizationMapper = organizationMapper;
        this.userOrganizationMapper = userOrganizationMapper; this.organizationAdminMapper = organizationAdminMapper;
        this.roleMapper = roleMapper; this.roleDataScopeMapper = roleDataScopeMapper; this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public void configureOrganizationAdministrator(Long operatorId, Long userId, Long organizationId) {
        requireSystemAdministrator(operatorId);
        if (userMapper.findById(userId) == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        if (organizationMapper.findById(organizationId) == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        if (!userOrganizationMapper.exists(userId, organizationId)) throw new IllegalStateException("组织管理员必须先关联对应组织");
        if (organizationAdminMapper.exists(userId, organizationId)) throw new IllegalStateException("用户已是该组织管理员");
        Long relationId = idGenerator.nextId();
        organizationAdminMapper.insert(relationId, userId, organizationId);
        auditService.record(IamChangeAuditEventType.ORGANIZATION_ADMIN_ASSIGN, operatorId,
                IamChangeTargetType.ORGANIZATION_ADMIN, userId, organizationId, organizationId,
                null, "ASSIGNED");
    }

    @Transactional
    public void configureRoleCustomScope(Long operatorId, Long roleId, Long organizationId) {
        requireSystemAdministrator(operatorId);
        var role = roleMapper.findById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        if (organizationMapper.findById(organizationId) == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        if (role.dataScope() != RoleDataScope.CUSTOM) {
            throw new IllegalArgumentException("角色不是自定义数据范围角色");
        }
        if (roleDataScopeMapper.exists(roleId, organizationId)) throw new IllegalStateException("角色已拥有该自定义组织范围");
        Long scopeId = idGenerator.nextId();
        roleDataScopeMapper.insert(scopeId, roleId, organizationId);
        auditService.record(IamChangeAuditEventType.ROLE_DATA_SCOPE_ADD, operatorId,
                IamChangeTargetType.ROLE_DATA_SCOPE, roleId, organizationId, organizationId,
                null, "ADDED");
    }

    private void requireSystemAdministrator(Long operatorId) {
        if (operatorId == null || !decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, "IAM_DATA_SCOPE_CONFIGURE")) {
            throw new SystemOperationAccessDeniedException("当前账号无配置数据范围权限");
        }
    }
}
