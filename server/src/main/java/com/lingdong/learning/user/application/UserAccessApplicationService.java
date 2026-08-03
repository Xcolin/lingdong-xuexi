package com.lingdong.learning.user.application;

import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Application service for user accounts, organization membership, and role grants.
 */
@Service
public class UserAccessApplicationService {
    private static final String GLOBAL_SCOPE_KEY = "GLOBAL";

    private final UserMapper userMapper;
    private final OrganizationMapper organizationMapper;
    private final RoleMapper roleMapper;
    private final UserOrganizationMapper userOrganizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final IdGenerator idGenerator;
    private final AuthenticationApplicationService authenticationApplicationService;

    public UserAccessApplicationService(
            UserMapper userMapper,
            OrganizationMapper organizationMapper,
            RoleMapper roleMapper,
            UserOrganizationMapper userOrganizationMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator,
            AuthenticationApplicationService authenticationApplicationService
    ) {
        this.userMapper = userMapper;
        this.organizationMapper = organizationMapper;
        this.roleMapper = roleMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
        this.authenticationApplicationService = authenticationApplicationService;
    }

    /**
     * Creates an account without a credential. Credential setup belongs to the later authentication flow.
     */
    @Transactional
    public User createUser(CreateUserCommand command) {
        Objects.requireNonNull(command, "创建用户请求不能为空");

        String username = requiredText(command.username(), "用户账号", 64);
        String displayName = requiredText(command.displayName(), "用户名称", 64);
        String mobile = optionalText(command.mobile(), 32);
        UserType type = Objects.requireNonNull(command.type(), "用户类型不能为空");

        if (userMapper.existsByUsername(username)) {
            throw new DuplicateUserAccountException(username);
        }
        if (mobile != null && userMapper.existsByMobile(mobile)) {
            throw new DuplicateUserAccountException(mobile);
        }

        User user = User.create(idGenerator.nextId(), username, displayName, mobile, type);
        try {
            userMapper.insert(user);
            return userMapper.findByUsername(username);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserAccountException(username);
        }
    }

    /**
     * Records an explicit personnel-to-organization association before an organization-scoped role is granted.
     */
    @Transactional
    public void associateWithOrganization(AssociateUserWithOrganizationCommand command) {
        Objects.requireNonNull(command, "用户组织关联请求不能为空");
        requireUser(command.userId());
        Organization organization = requireOrganization(command.organizationId());
        if (organization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("组织已停用，不能建立用户组织关联：" + organization.id());
        }
        if (userOrganizationMapper.exists(command.userId(), command.organizationId())) {
            throw new IllegalStateException("用户已关联该组织");
        }
        try {
            userOrganizationMapper.insert(idGenerator.nextId(), command.userId(), command.organizationId());
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("用户已关联该组织");
        }
    }

    /**
     * Grants a role in a global or one-organization scope. Caller authorization is enforced later by security filters.
     */
    @Transactional
    public void assignRole(AssignRoleToUserCommand command) {
        Objects.requireNonNull(command, "用户角色授权请求不能为空");
        requireUser(command.userId());
        Role role = requireRole(command.roleId());
        if (role.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("角色已停用，不能授予用户：" + role.code());
        }

        String scopeKey = resolveScopeKey(command.userId(), command.organizationId());
        if (userRoleMapper.exists(command.userId(), command.roleId(), scopeKey)) {
            throw new DuplicateUserRoleAssignmentException();
        }

        try {
            userRoleMapper.insert(idGenerator.nextId(), command.userId(), command.roleId(), command.organizationId(), scopeKey);
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserRoleAssignmentException();
        }
    }

    /** 更新账号状态；停用或锁定后立即撤销该账号的活动设备会话。 */
    @Transactional
    public User updateStatus(UpdateUserStatusCommand command) {
        Objects.requireNonNull(command, "用户状态变更请求不能为空");
        User user = requireUser(command.userId());
        UserStatus targetStatus = Objects.requireNonNull(command.status(), "用户状态不能为空");
        if (user.status() == targetStatus) {
            return user;
        }
        if (userMapper.updateStatus(user.id(), targetStatus) != 1) {
            throw new IllegalStateException("用户状态更新失败");
        }
        if (targetStatus != UserStatus.ENABLED) {
            authenticationApplicationService.revokeAllActiveSessionsForUser(user.id());
        }
        return requireUser(user.id());
    }

    private String resolveScopeKey(Long userId, Long organizationId) {
        if (organizationId == null) {
            return GLOBAL_SCOPE_KEY;
        }

        Organization organization = requireOrganization(organizationId);
        if (organization.status() != OrganizationStatus.ENABLED) {
            throw new IllegalStateException("组织已停用，不能授予组织范围角色：" + organization.id());
        }
        if (!userOrganizationMapper.exists(userId, organizationId)) {
            throw new IllegalStateException("用户尚未建立该组织关联，不能授予组织范围角色");
        }
        return "ORG:" + organizationId;
    }

    private User requireUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return user;
    }

    private Organization requireOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new IllegalArgumentException("组织标识不能为空");
        }
        Organization organization = organizationMapper.findById(organizationId);
        if (organization == null) {
            throw new ResourceNotFoundException("组织不存在：" + organizationId);
        }
        return organization;
    }

    private Role requireRole(Long roleId) {
        if (roleId == null) {
            throw new IllegalArgumentException("角色标识不能为空");
        }
        Role role = roleMapper.findById(roleId);
        if (role == null) {
            throw new ResourceNotFoundException("角色不存在：" + roleId);
        }
        return role;
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
