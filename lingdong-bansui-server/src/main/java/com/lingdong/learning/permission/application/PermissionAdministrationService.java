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
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.lingdong.learning.permission.domain.PermissionEffect;

/** 管理 RBAC 权限目录、角色权限效果和用户显式权限效果。 */
@Service
public class PermissionAdministrationService {
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private static final Pattern CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final IdGenerator idGenerator;
    private final IamChangeAuditService auditService;
    private final com.lingdong.learning.menu.infrastructure.persistence.MenuMapper menuMapper;

    public PermissionAdministrationService(
            PermissionMapper permissionMapper,
            RoleMapper roleMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            RolePermissionMapper rolePermissionMapper,
            UserPermissionMapper userPermissionMapper,
            IdGenerator idGenerator,
            IamChangeAuditService auditService,
            com.lingdong.learning.menu.infrastructure.persistence.MenuMapper menuMapper, com.lingdong.learning.permission.application.PermissionDecisionService decisions) {
        this.decisions = decisions;
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
        this.menuMapper = menuMapper;
    }

    @Transactional
    public Permission createPermission(CreatePermissionCommand command) {
        requirePermission(command.operatorId(), "IAM_ROLE_PERMISSION_GRANT");
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
        requirePermission(command.operatorId(), "IAM_ROLE_PERMISSION_GRANT");
        rolePermissionMapper.lockRole(command.roleId());
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
        requirePermission(command.operatorId(), "IAM_ROLE_PERMISSION_GRANT");
        rolePermissionMapper.lockRole(command.roleId());
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
        requirePermission(operatorId, "IAM_ROLE_PERMISSION_GRANT");
        if (roleMapper.findById(roleId) == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        return rolePermissionMapper.findByRoleId(roleId);
    }

    public List<com.lingdong.learning.menu.domain.MenuNode> listRolePermissionMenus(Long operatorId) {
        requirePermission(operatorId, "IAM_ROLE_PERMISSION_GRANT");
        return menuMapper.findAll();
    }

    @Transactional
    public void removeRolePermission(Long operatorId, Long roleId, Long permissionId) {
        requirePermission(operatorId, "IAM_ROLE_PERMISSION_GRANT");
        rolePermissionMapper.lockRole(roleId);
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

    /** Replace only explicit ALLOWs within the displayed scope; all validation precedes mutations. */
    @Transactional
    public void batchRolePermissions(BatchRolePermissionsCommand command) {
        requirePermission(command.operatorId(), "IAM_ROLE_PERMISSION_GRANT");
        if (command.roleId() == null || rolePermissionMapper.lockRole(command.roleId()) == null) {
            throw new ResourceNotFoundException("角色不存在：" + command.roleId());
        }
        Set<Long> selected = uniqueIds(command.permissionIds());
        Set<Long> managed = uniqueIds(command.managedPermissionIds());
        if (!managed.containsAll(selected)) throw new IllegalArgumentException("选中权限必须属于本次管理范围");
        for (Long id : managed) {
            if (permissionMapper.findById(id) == null) throw new ResourceNotFoundException("权限不存在：" + id);
        }
        selected.forEach(this::requireEnabledPermission);
        Map<Long, PermissionEffect> expected = assignmentEffects(command.expectedAssignments());
        // Locking reads see the latest committed assignments even on MySQL REPEATABLE READ.
        Map<Long, PermissionEffect> current = assignmentEffects(rolePermissionMapper.lockByRoleId(command.roleId()));
        if (!expected.equals(current)) throw new IllegalStateException("角色权限已变更，请重新选择角色并加载后再保存");
        for (Long id : selected) {
            if (current.get(id) == PermissionEffect.DENY) throw new IllegalStateException("已有禁止权限不能通过树形授权修改：" + id);
        }
        for (Long id : managed) {
            PermissionEffect before = current.get(id);
            if (before == PermissionEffect.ALLOW && !selected.contains(id)) {
                rolePermissionMapper.delete(command.roleId(), id);
                auditService.record(IamChangeAuditEventType.ROLE_PERMISSION_REMOVE, command.operatorId(),
                        IamChangeTargetType.ROLE_PERMISSION, command.roleId(), id, null, "ALLOW", null);
            } else if (before == null && selected.contains(id)) {
                rolePermissionMapper.insert(idGenerator.nextId(), command.roleId(), id, PermissionEffect.ALLOW);
                auditService.record(IamChangeAuditEventType.ROLE_PERMISSION_CONFIGURE, command.operatorId(),
                        IamChangeTargetType.ROLE_PERMISSION, command.roleId(), id, null, null, "ALLOW");
            }
        }
    }

    public UserPermissionTree userPermissionTree(Long operatorId, Long userId) {
        requirePermission(operatorId, "IAM_USER_PERMISSION_CONFIGURE");
        if (userMapper.findById(userId) == null) throw new ResourceNotFoundException("用户不存在：" + userId);
        List<PermissionAssignment> inherited = rolePermissionMapper.findInheritedByUserId(userId);
        Set<Long> denied = inherited.stream().filter(a -> a.effect() == PermissionEffect.DENY)
                .map(PermissionAssignment::permissionId).collect(java.util.stream.Collectors.toSet());
        List<Long> allowed = inherited.stream().filter(a -> a.effect() == PermissionEffect.ALLOW && !denied.contains(a.permissionId()))
                .map(PermissionAssignment::permissionId).distinct().sorted().toList();
        return new UserPermissionTree(menuMapper.findAll(), allowed, denied.stream().sorted().toList());
    }

    @Transactional
    public void batchUserPermissions(BatchUserPermissionsCommand command) {
        requirePermission(command.operatorId(), "IAM_USER_PERMISSION_CONFIGURE");
        if (command.userId() == null || userMapper.findByIdForUpdate(command.userId()) == null)
            throw new ResourceNotFoundException("用户不存在：" + command.userId());
        Set<Long> selected = uniqueIds(command.permissionIds());
        Set<Long> managed = uniqueIds(command.managedPermissionIds());
        if (!managed.containsAll(selected)) throw new IllegalArgumentException("选中权限必须属于本次管理范围");
        for (Long id : managed) {
            if (permissionMapper.findById(id) == null) throw new ResourceNotFoundException("权限不存在：" + id);
        }
        selected.forEach(this::requireEnabledPermission);
        Map<Long, PermissionEffect> expected = assignmentEffects(command.expectedAssignments());
        Map<Long, PermissionEffect> current = assignmentEffects(userPermissionMapper.lockByUserId(command.userId()));
        if (!expected.equals(current)) throw new IllegalStateException("用户权限已变更，请重新加载后再保存");
        Set<Long> inherited = rolePermissionMapper.lockInheritedByUserId(command.userId()).stream()
                .map(PermissionAssignment::permissionId).collect(java.util.stream.Collectors.toSet());
        if (managed.stream().anyMatch(inherited::contains)) throw new IllegalStateException("继承权限为只读，不能通过用户授权修改");
        if (!selected.isEmpty() && !userRoleMapper.hasAnyEnabledRole(command.userId()))
            throw new IllegalStateException("用户补充允许必须建立在活动角色基础上");
        for (Long id : selected) {
            if (current.get(id) == PermissionEffect.DENY) throw new IllegalStateException("已有禁止权限不能通过树形授权修改：" + id);
        }
        for (Long id : managed) {
            PermissionEffect before = current.get(id);
            if (before == PermissionEffect.ALLOW && !selected.contains(id)) {
                userPermissionMapper.delete(command.userId(), id);
                auditService.record(IamChangeAuditEventType.USER_PERMISSION_REMOVE, command.operatorId(),
                        IamChangeTargetType.USER_PERMISSION, command.userId(), id, null, "ALLOW", null);
            } else if (before == null && selected.contains(id)) {
                userPermissionMapper.insert(idGenerator.nextId(), command.userId(), id, PermissionEffect.ALLOW);
                auditService.record(IamChangeAuditEventType.USER_PERMISSION_CONFIGURE, command.operatorId(),
                        IamChangeTargetType.USER_PERMISSION, command.userId(), id, null, null, "ALLOW");
            }
        }
    }

    private Set<Long> uniqueIds(List<Long> ids) {
        if (ids == null || ids.stream().anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("权限集合不能为空或包含空项");
        Set<Long> unique = new HashSet<>(ids);
        if (unique.size() != ids.size()) throw new IllegalArgumentException("权限集合不能包含重复项");
        return unique;
    }

    private Map<Long, PermissionEffect> assignmentEffects(List<PermissionAssignment> assignments) {
        if (assignments == null) throw new IllegalArgumentException("权限快照不能为空");
        Map<Long, PermissionEffect> result = new HashMap<>();
        for (PermissionAssignment assignment : assignments) {
            if (assignment == null || assignment.permissionId() == null || assignment.effect() == null
                    || result.putIfAbsent(assignment.permissionId(), assignment.effect()) != null) {
                throw new IllegalArgumentException("权限快照格式无效");
            }
        }
        return result;
    }

    @Transactional
    public void configureUserPermission(ConfigureUserPermissionCommand command) {
        requirePermission(command.operatorId(), "IAM_USER_PERMISSION_CONFIGURE");
        if (userMapper.findByIdForUpdate(command.userId()) == null) {
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
        var previousEffect = assignmentEffects(userPermissionMapper.lockByUserId(command.userId())).get(command.permissionId());
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
        requirePermission(operatorId, "IAM_USER_PERMISSION_CONFIGURE");
        if (userMapper.findById(userId) == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return userPermissionMapper.findByUserId(userId);
    }

    @Transactional
    public void removeUserPermission(Long operatorId, Long userId, Long permissionId) {
        requirePermission(operatorId, "IAM_USER_PERMISSION_CONFIGURE");
        if (userMapper.findByIdForUpdate(userId) == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        if (permissionMapper.findById(permissionId) == null) {
            throw new ResourceNotFoundException("权限不存在：" + permissionId);
        }
        var previousEffect = assignmentEffects(userPermissionMapper.lockByUserId(userId)).get(permissionId);
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

    private void requirePermission(Long operatorId, String permissionCode) {
        User operator = operatorId == null ? null : userMapper.findById(operatorId);
        if (operator == null || operator.status() != UserStatus.ENABLED
                || !decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, permissionCode)) {
            throw new SystemOperationAccessDeniedException("当前账号无管理权限授权资格");
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
