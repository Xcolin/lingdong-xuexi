package com.lingdong.learning.user.application;

import com.lingdong.learning.auth.application.AuthenticationApplicationService;
import com.lingdong.learning.common.id.IdGenerator;
import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.audit.application.IamChangeAuditEventType;
import com.lingdong.learning.iam.audit.application.IamChangeAuditService;
import com.lingdong.learning.iam.audit.application.IamChangeTargetType;
import com.lingdong.learning.iam.domain.RoleStatus;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
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

import java.util.List;
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
    private final IamChangeAuditService auditService;

    public UserAccessApplicationService(
            UserMapper userMapper,
            OrganizationMapper organizationMapper,
            RoleMapper roleMapper,
            UserOrganizationMapper userOrganizationMapper,
            UserRoleMapper userRoleMapper,
            IdGenerator idGenerator,
            AuthenticationApplicationService authenticationApplicationService,
            IamChangeAuditService auditService
    ) {
        this.userMapper = userMapper;
        this.organizationMapper = organizationMapper;
        this.roleMapper = roleMapper;
        this.userOrganizationMapper = userOrganizationMapper;
        this.userRoleMapper = userRoleMapper;
        this.idGenerator = idGenerator;
        this.authenticationApplicationService = authenticationApplicationService;
        this.auditService = auditService;
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
            User created = userMapper.findByUsername(username);
            auditService.record(IamChangeAuditEventType.USER_CREATE, command.operatorId(),
                    IamChangeTargetType.USER, created.id(), null, null, null, created.type().name());
            return created;
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserAccountException(username);
        }
    }

    /** Creates account and initial organization membership in one transaction. */
    @Transactional
    public User createUserInOrganization(CreateUserCommand command, Long organizationId) {
        Organization organization = organizationId == null ? null : organizationMapper.findByIdForUpdate(organizationId);
        if (organization == null) throw new ResourceNotFoundException("组织不存在：" + organizationId);
        if (!OrganizationOperationalStatusService.isOperational(organization)) throw new IllegalStateException("组织已停用，不能创建用户");
        User created = createUser(command);
        associateWithOrganization(new AssociateUserWithOrganizationCommand(created.id(), organizationId, command.operatorId()));
        return created;
    }

    /** Creates a managed Web account, organization membership and initial credential atomically. */
    @Transactional
    public User createManagedUserInOrganization(CreateUserCommand command, Long organizationId, String password) {
        if (command.type() == UserType.STUDENT && password != null && !password.isBlank()) {
            throw new IllegalArgumentException("学生密码请使用学生凭据管理流程");
        }
        String passwordHash = command.type() == UserType.STUDENT ? null
                : authenticationApplicationService.encodeManagedUserPassword(password);
        User created = createUserInOrganization(command, organizationId);
        if (passwordHash != null) {
            if (userMapper.updatePasswordHash(created.id(), passwordHash) != 1) {
                throw new IllegalStateException("初始密码保存失败");
            }
        }
        return userMapper.findById(created.id());
    }

    /**
     * Records an explicit personnel-to-organization association before an organization-scoped role is granted.
     */
    @Transactional
    public void associateWithOrganization(AssociateUserWithOrganizationCommand command) {
        Objects.requireNonNull(command, "用户组织关联请求不能为空");
        requireUser(command.userId());
        Organization organization = requireOrganization(command.organizationId());
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
            throw new IllegalStateException("组织已停用，不能建立用户组织关联：" + organization.id());
        }
        if (userOrganizationMapper.exists(command.userId(), command.organizationId())) {
            throw new IllegalStateException("用户已关联该组织");
        }
        try {
            Long relationId = idGenerator.nextId();
            userOrganizationMapper.insert(relationId, command.userId(), command.organizationId());
            auditService.record(IamChangeAuditEventType.USER_ORGANIZATION_ASSOCIATE, command.operatorId(),
                    IamChangeTargetType.USER_ORGANIZATION, command.userId(), command.organizationId(),
                    command.organizationId(), null, "ASSOCIATED");
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
            Long assignmentId = idGenerator.nextId();
            userRoleMapper.insert(assignmentId, command.userId(), command.roleId(), command.organizationId(), scopeKey);
            auditService.record(IamChangeAuditEventType.USER_ROLE_ASSIGN, command.operatorId(),
                    IamChangeTargetType.USER_ROLE, command.userId(), command.roleId(),
                    command.organizationId(), null, "ASSIGNED");
        } catch (DuplicateKeyException exception) {
            throw new DuplicateUserRoleAssignmentException();
        }
    }

    /**
     * 角色侧批量授予用户：逐用户复用既有授予用例（保留组织范围校验与逐条审计），
     * 组织范围角色必填组织；任一失败整批回滚并报告失败用户及原因。
     */
    @Transactional
    public void batchAssignRole(BatchAssignRoleToRoleCommand command) {
        Objects.requireNonNull(command, "批量授予请求不能为空");
        if (command.assignments() == null || command.assignments().isEmpty() || command.assignments().size() > 200) {
            throw new IllegalArgumentException("批量授予名单无效");
        }
        Role role = requireRole(command.roleId());
        if (role.status() != RoleStatus.ENABLED) {
            throw new IllegalStateException("角色已停用，不能授予用户：" + role.code());
        }
        boolean organizationScoped = role.dataScope() != null && role.dataScope() != com.lingdong.learning.iam.domain.RoleDataScope.ALL;
        List<String> failures = new java.util.ArrayList<>();
        // 预检阶段：先全量校验再写入，保证任一失败时不产生半批写入（并发冲突仍由唯一约束与事务兜底）
        for (BatchRoleAssignmentItem item : command.assignments()) {
            try {
                if (item == null || item.userId() == null) {
                    throw new IllegalArgumentException("用户标识不能为空");
                }
                if (organizationScoped && item.organizationId() == null) {
                    throw new IllegalArgumentException("组织范围角色必须指定组织");
                }
                if (!organizationScoped && item.organizationId() != null) {
                    throw new IllegalArgumentException("全局数据范围角色不需要指定组织");
                }
                requireUser(item.userId());
                String scopeKey = resolveScopeKey(item.userId(), item.organizationId());
                if (userRoleMapper.exists(item.userId(), command.roleId(), scopeKey)) {
                    throw new DuplicateUserRoleAssignmentException();
                }
            } catch (RuntimeException exception) {
                failures.add(item == null || item.userId() == null ? "无效条目（" + exception.getMessage() + "）"
                        : item.userId() + "（" + exception.getMessage() + "）");
            }
        }
        if (!failures.isEmpty()) {
            throw new BatchRoleAssignmentException(failures);
        }
        for (BatchRoleAssignmentItem item : command.assignments()) {
            assignRole(new AssignRoleToUserCommand(item.userId(), command.roleId(), item.organizationId(), command.operatorId()));
        }
    }

    /** 更新账号状态；停用或锁定后立即撤销该账号的活动设备会话。 */
    @Transactional
    public User updateStatus(UpdateUserStatusCommand command) {
        Objects.requireNonNull(command, "用户状态变更请求不能为空");
        User user = requireUser(command.userId());
        UserStatus targetStatus = Objects.requireNonNull(command.status(), "用户状态不能为空");
        if (targetStatus == UserStatus.CANCELLED || user.status() == UserStatus.CANCELLED) {
            throw new IllegalArgumentException("已注销状态只能由账号注销流程维护");
        }
        if (user.status() == targetStatus) {
            return user;
        }
        if (userMapper.updateStatus(user.id(), targetStatus) != 1) {
            throw new IllegalStateException("用户状态更新失败");
        }
        if (targetStatus != UserStatus.ENABLED) {
            authenticationApplicationService.revokeAllActiveSessionsForUser(user.id());
        }
        auditService.record(IamChangeAuditEventType.USER_STATUS_CHANGE, command.operatorId(),
                IamChangeTargetType.USER, user.id(), null, null, user.status().name(), targetStatus.name());
        return requireUser(user.id());
    }

    private String resolveScopeKey(Long userId, Long organizationId) {
        if (organizationId == null) {
            return GLOBAL_SCOPE_KEY;
        }

        Organization organization = requireOrganization(organizationId);
        if (!OrganizationOperationalStatusService.isOperational(organization)) {
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
