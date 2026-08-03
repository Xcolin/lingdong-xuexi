package com.lingdong.learning.permission.application;

import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.UserPermissionMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/** Manages RBAC catalog entries and explicit user-level permission effects. */
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

    public PermissionAdministrationService(
            PermissionMapper permissionMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            UserPermissionMapper userPermissionMapper,
            IdGenerator idGenerator
    ) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.idGenerator = idGenerator;
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
            return permissionMapper.findByCode(code);
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
        if (permissionMapper.findById(command.permissionId()) == null) {
            throw new ResourceNotFoundException("权限不存在：" + command.permissionId());
        }
        if (rolePermissionMapper.exists(command.roleId(), command.permissionId())) {
            throw new IllegalStateException("角色已拥有该权限");
        }
        rolePermissionMapper.insert(idGenerator.nextId(), command.roleId(), command.permissionId());
    }

    @Transactional
    public void configureUserPermission(ConfigureUserPermissionCommand command) {
        requireSystemAdministrator(command.operatorId());
        if (userMapper.findById(command.userId()) == null) {
            throw new ResourceNotFoundException("用户不存在：" + command.userId());
        }
        if (permissionMapper.findById(command.permissionId()) == null) {
            throw new ResourceNotFoundException("权限不存在：" + command.permissionId());
        }
        if (command.effect() == null) {
            throw new IllegalArgumentException("权限效果不能为空");
        }
        if (userPermissionMapper.findEffect(command.userId(), command.permissionId()) == null) {
            userPermissionMapper.insert(idGenerator.nextId(), command.userId(), command.permissionId(), command.effect());
        } else {
            userPermissionMapper.update(command.userId(), command.permissionId(), command.effect());
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
        if (operatorId == null || !userRoleMapper.hasRoleCode(operatorId, "SYS_ADMIN")) {
            throw new SystemOperationAccessDeniedException("仅系统管理员可管理权限");
        }
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
