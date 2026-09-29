package com.lingdong.learning.permission.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.UserPermissionMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;
import java.util.List;

/** 管理 RBAC 权限目录、角色权限效果和用户显式权限效果。 */
@Service
public class PermissionAdministrationService {
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final IdGenerator idGenerator;
    private final IamChangeAuditService auditService;

    public PermissionAdministrationService(
            PermissionMapper permissionMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            UserPermissionMapper userPermissionMapper,
            IdGenerator idGenerator,
            IamChangeAuditService auditService
    ) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public Permission createPermission(CreatePermissionCommand command) {
        requireSystemAdministrator(command.operatorId());
        String code = validatePermissionCode(command.code());
        String name = requiredText(command.name(), "权限名称", 128);
        if (command.resourceType() == null || command.client() == null) {
            throw new IllegalArgumentException("权限资源类型和客户端不能为空");
        }
        if (command.parentId() != null && permissionMapper.findById(command.parentId()) == null) {
            throw new ResourceNotFoundException("父级权限不存在：" + command.parentId());
        }
        if (permissionMapper.existsByCode(code)) {
            throw new IllegalStateException("权限编码已存在：" + code);
        }

        Permission permission = new Permission(
                idGenerator.nextId(), code, name, command.resourceType(), command.client(), command.parentId(), PermissionStatus.ENABLED, null
        );
        try {
            permissionMapper.insert(permission);
            Permission created = permissionMapper.findByCode(code);
            auditService.record(IamChangeAuditEventType.PERMISSION_CREATE, command.operatorId(),
                    IamChangeTargetType.PERMISSION, created.id(), created.parentId(), null,
                    null, created.client().name() + ":" + created.resourceType().name());
            return created;
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("权限编码已存在：" + code);
        }
    }

    @Transactional
    public void grantRolePermission(GrantRolePermissionCommand command) {
        requireSystemAdministrator(command.operatorId());
        if (roleMapper.findById(command.roleId()) == null) {
            throw new ResourceNotFoundException("角色不存在：" + command.roleId());
        }
        requireEnabledPermission(command.permissionId());
        if (rolePermissionMapper.exists(command.roleId(), command.permissionId())) {
            throw new IllegalStateException("角色已拥有该权限");
        }
        rolePermissionMapper.insert(
                idGenerator.nextId(), command.roleId(), command.permissionId(),
                com.lingdong.learning.permission.domain.PermissionEffect.ALLOW);
        auditService.record(IamChangeAuditEventType.ROLE_PERMISSION_CONFIGURE, command.operatorId(),
                IamChangeTargetType.ROLE_PERMISSION, command.roleId(), command.permissionId(), null,
                null, com.lingdong.learning.permission.domain.PermissionEffect.ALLOW.name());
    }

    @Transactional
    public void configureRolePermission(ConfigureRolePermissionCommand command) {
        requireSystemAdministrator(command.operatorId());
        if (roleMapper.findById(command.roleId()) == null) {
            throw new ResourceNotFoundException("角色不存在：" + command.roleId());
        }
        requireEnabledPermission(command.permissionId());
        if (command.effect() == null) {
            throw new IllegalArgumentException("权限效果不能为空");
        }
        var previousEffect = rolePermissionMapper.findEffect(command.roleId(), command.permissionId());
        if (previousEffect != null) {
            if (previousEffect == command.effect()) return;
            rolePermissionMapper.updateEffect(command.roleId(), command.permissionId(), command.effect());
        } else {
            rolePermissionMapper.insert(
                    idGenerator.nextId(), command.roleId(), command.permissionId(), command.effect());
        }
        auditService.record(IamChangeAuditEventType.ROLE_PERMISSION_CONFIGURE, command.operatorId(),
                IamChangeTargetType.ROLE_PERMISSION, command.roleId(), command.permissionId(), null,
                previousEffect == null ? null : previousEffect.name(), command.effect().name());
    }

    public List<PermissionAssignment> listRolePermissions(Long operatorId, Long roleId) {
        requireSystemAdministrator(operatorId);
        if (roleMapper.findById(roleId) == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        return rolePermissionMapper.findByRoleId(roleId);
    }

    @Transactional
    public void removeRolePermission(Long operatorId, Long roleId, Long permissionId) {
        requireSystemAdministrator(operatorId);
        if (roleMapper.findById(roleId) == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        if (permissionMapper.findById(permissionId) == null) {
            throw new ResourceNotFoundException("权限不存在：" + permissionId);
        }
        var previousEffect = rolePermissionMapper.findEffect(roleId, permissionId);
        if (previousEffect != null && rolePermissionMapper.delete(roleId, permissionId) == 1) {
            auditService.record(IamChangeAuditEventType.ROLE_PERMISSION_REMOVE, operatorId,
                    IamChangeTargetType.ROLE_PERMISSION, roleId, permissionId, null,
                    previousEffect.name(), null);
        }
    }

    @Transactional
    public void configureUserPermission(ConfigureUserPermissionCommand command) {
        requireSystemAdministrator(command.operatorId());
        if (userMapper.findById(command.userId()) == null) {
            throw new ResourceNotFoundException("用户不存在：" + command.userId());
        }
        requireEnabledPermission(command.permissionId());
        if (command.effect() == null) {
            throw new IllegalArgumentException("权限效果不能为空");
        }
        if (command.effect() == com.lingdong.learning.permission.domain.PermissionEffect.ALLOW
                && !userRoleMapper.hasAnyEnabledRole(command.userId())) {
            throw new IllegalStateException("用户补充允许必须建立在活动角色基础上");
        }
        var previousEffect = userPermissionMapper.findEffect(command.userId(), command.permissionId());
        if (previousEffect == command.effect()) return;
        if (previousEffect == null) {
            userPermissionMapper.insert(idGenerator.nextId(), command.userId(), command.permissionId(), command.effect());
        } else {
            userPermissionMapper.update(command.userId(), command.permissionId(), command.effect());
        }
        auditService.record(IamChangeAuditEventType.USER_PERMISSION_CONFIGURE, command.operatorId(),
                IamChangeTargetType.USER_PERMISSION, command.userId(), command.permissionId(), null,
                previousEffect == null ? null : previousEffect.name(), command.effect().name());
    }

    public List<PermissionAssignment> listUserPermissions(Long operatorId, Long userId) {
        requireSystemAdministrator(operatorId);
        if (userMapper.findById(userId) == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return userPermissionMapper.findByUserId(userId);
    }

    @Transactional
    public void removeUserPermission(Long operatorId, Long userId, Long permissionId) {
        requireSystemAdministrator(operatorId);
        if (userMapper.findById(userId) == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        if (permissionMapper.findById(permissionId) == null) {
            throw new ResourceNotFoundException("权限不存在：" + permissionId);
        }
        var previousEffect = userPermissionMapper.findEffect(userId, permissionId);
        if (previousEffect != null && userPermissionMapper.delete(userId, permissionId) == 1) {
            auditService.record(IamChangeAuditEventType.USER_PERMISSION_REMOVE, operatorId,
                    IamChangeTargetType.USER_PERMISSION, userId, permissionId, null,
                    previousEffect.name(), null);
        }
    }

    private String validatePermissionCode(String code) {
        String normalized = requiredText(code, "权限编码", 128);
        if (!CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("权限编码仅允许大写字母、数字和下划线");
        }
        return normalized;
    }

    private void requireSystemAdministrator(Long operatorId) {
        User operator = operatorId == null ? null : userMapper.findById(operatorId);
        if (operator == null || operator.status() != UserStatus.ENABLED
                || !userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")) {
            throw new SystemOperationAccessDeniedException("仅启用中的系统管理员可管理权限");
        }
    }

    private Permission requireEnabledPermission(Long permissionId) {
        Permission permission = permissionMapper.findById(permissionId);
        if (permission == null) {
            throw new ResourceNotFoundException("权限不存在：" + permissionId);
        }
        if (permission.status() != PermissionStatus.ENABLED) {
            throw new IllegalStateException("权限已停用，不能配置授权效果：" + permissionId);
        }
        return permission;
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
