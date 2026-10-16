package com.lingdong.learning.iam.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.iam.domain.RoleDataScope;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Application service for custom role lifecycle commands.
 */
@Service
public class RoleApplicationService {
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,63}");

    private final com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles;
    private final RoleMapper roleMapper;
    private final IdGenerator idGenerator;
    private final IamChangeAuditService auditService;

    public RoleApplicationService(RoleMapper roleMapper, IdGenerator idGenerator, IamChangeAuditService auditService, com.lingdong.learning.permission.application.PermissionDecisionService decisions, com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper userRoles) {
        this.decisions = decisions;
        this.userRoles = userRoles;
        this.roleMapper = roleMapper;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    /**
     * Keeps validation, duplicate detection and persistence in one transaction. The database unique
     * constraint remains the final guard when concurrent administrators submit the same role code.
     */
    @Transactional
    public Role createCustomRole(CreateCustomRoleCommand command) {
        Objects.requireNonNull(command, "创建角色请求不能为空");
        if (command.operatorId() != null && !decisions.isAllowed(command.operatorId(),
                com.lingdong.learning.permission.domain.PermissionClient.WEB, "IAM_ROLE_CREATE"))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无创建角色权限");

        String code = requiredText(command.code(), "角色编码", 64);
        String name = requiredText(command.name(), "角色名称", 64);
        String description = optionalText(command.description(), 512);
        RoleDataScope dataScope = Objects.requireNonNull(command.dataScope(), "数据权限范围不能为空");

        if (!ROLE_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("角色编码仅允许3至64位大写字母、数字和下划线，且必须以字母开头");
        }
        if (roleMapper.existsByCode(code)) {
            throw new DuplicateRoleCodeException(code);
        }

        Role role = Role.custom(idGenerator.nextId(), code, name, description, dataScope);
        try {
            roleMapper.insert(role);
            Role created = roleMapper.findByCode(code);
            auditService.record(IamChangeAuditEventType.ROLE_CREATE, command.operatorId(),
                    IamChangeTargetType.ROLE, created.id(), null, null, null, created.dataScope().name());
            return created;
        } catch (DuplicateKeyException exception) {
            throw new DuplicateRoleCodeException(code);
        }
    }

    @Transactional
    public Role updateRole(Long operatorId, Long roleId, String name, String description) {
        requirePermission(operatorId, "IAM_ROLE_UPDATE");
        Role current = roleMapper.lockById(roleId);
        if (current == null) throw new IllegalArgumentException("角色不存在");
        if ((current.builtIn() || current.type() == com.lingdong.learning.iam.domain.RoleType.BUILT_IN)
                && !userRoles.findEnabledRoleCodesByUserId(operatorId).contains("SYS_ADMIN"))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("内置角色仅系统管理员可编辑");
        String normalizedName = requiredText(name, "角色名称", 64);
        String normalizedDescription = optionalText(description, 512);
        roleMapper.updateDetails(roleId, normalizedName, normalizedDescription);
        auditService.record(IamChangeAuditEventType.ROLE_UPDATE, operatorId, IamChangeTargetType.ROLE,
                roleId, null, null, current.name(), normalizedName);
        return roleMapper.findById(roleId);
    }

    @Transactional
    public Role changeRoleStatus(Long operatorId, Long roleId, com.lingdong.learning.iam.domain.RoleStatus status) {
        requirePermission(operatorId, "IAM_ROLE_STATUS_CHANGE");
        Role current = requireCustomRole(roleId);
        if (status == null) throw new IllegalArgumentException("角色状态不能为空");
        if (current.status() != status) {
            roleMapper.updateStatus(roleId, status);
            roleMapper.revokeAssociatedSessions(roleId);
            auditService.record(IamChangeAuditEventType.ROLE_STATUS_CHANGE, operatorId, IamChangeTargetType.ROLE,
                    roleId, null, null, current.status().name(), status.name());
        }
        return roleMapper.findById(roleId);
    }

    @Transactional
    public void deleteRole(Long operatorId, Long roleId) {
        requirePermission(operatorId, "IAM_ROLE_DELETE");
        Role current = requireCustomRole(roleId);
        if (roleMapper.hasUserAssignments(roleId)) throw new IllegalArgumentException("角色已关联用户，请先撤销用户角色后再删除");
        roleMapper.deletePermissions(roleId);
        roleMapper.deleteDataScopes(roleId);
        roleMapper.deleteById(roleId);
        auditService.record(IamChangeAuditEventType.ROLE_DELETE, operatorId, IamChangeTargetType.ROLE,
                roleId, null, null, current.code(), null);
    }

    private Role requireCustomRole(Long roleId) {
        Role role = roleMapper.lockById(roleId);
        if (role == null) throw new IllegalArgumentException("角色不存在");
        if (role.builtIn() || role.type() == com.lingdong.learning.iam.domain.RoleType.BUILT_IN)
            throw new IllegalArgumentException("内置角色不允许编辑、删除或启停");
        return role;
    }

    private void requirePermission(Long operatorId, String code) {
        if (operatorId == null || !decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, code))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无此角色管理权限");
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String normalized = optionalText(value, maxLength);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return normalized;
    }

    private String optionalText(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("文本长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
