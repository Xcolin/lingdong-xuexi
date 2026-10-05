package com.lingdong.learning.iam.application;

import com.lingdong.learning.common.web.ResourceNotFoundException;
import com.lingdong.learning.iam.domain.Role;
import com.lingdong.learning.iam.infrastructure.persistence.RoleMapper;
import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 为 IAM HTTP 接口提供不包含凭证信息的查询用例边界。 */
@Service
public class IamQueryApplicationService {
    private final com.lingdong.learning.permission.application.PermissionDecisionService decisions;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper userOrganizationMapper;

    public IamQueryApplicationService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            PermissionMapper permissionMapper,
            com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper userOrganizationMapper, com.lingdong.learning.permission.application.PermissionDecisionService decisions) {
        this.decisions = decisions;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userOrganizationMapper = userOrganizationMapper;
    }

    public User findUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户标识不能为空");
        }
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在：" + userId);
        }
        return user;
    }

    /** 按已校验条件查询用户目录，避免把分页边界留给控制器或 XML。 */
    public UserDirectoryPage listUsers(String keyword, UserType type, UserStatus status, int page, int pageSize) {
        return listUsers(keyword, type, status, null, page, pageSize);
    }

    public UserDirectoryPage listUsers(String keyword, UserType type, UserStatus status, Long organizationId, int page, int pageSize) {
        if (organizationId != null && organizationId <= 0) throw new IllegalArgumentException("组织标识必须为正数");
        if (page < 1 || page > 1_000_000) {
            throw new IllegalArgumentException("页码必须在 1 至 1000000 之间");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("每页数量必须在 1 至 100 之间");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        UserDirectoryQuery query = new UserDirectoryQuery(
                normalizedKeyword, type, status, organizationId, Math.multiplyExact(page - 1, pageSize), pageSize
        );
        List<User> items = userMapper.findPage(query);
        java.util.Map<Long, List<String>> names = new java.util.LinkedHashMap<>();
        if (!items.isEmpty()) {
            for (var relation : userOrganizationMapper.findNamesByUserIds(items.stream().map(User::id).toList())) {
                names.computeIfAbsent(relation.userId(), ignored -> new java.util.ArrayList<>()).add(relation.name());
            }
        }
        return new UserDirectoryPage(items, page, pageSize, userMapper.count(query), names);
    }

    public List<Role> listRoles() {
        return roleMapper.findAll();
    }

    public List<Permission> listPermissions() {
        return permissionMapper.findAll();
    }

    public User findUser(Long operatorId, Long userId) {
        requirePermission(operatorId, "IAM_USER_READ");
        return findUser(userId);
    }
    public UserDirectoryPage listUsers(Long operatorId, String keyword, UserType type, UserStatus status,
            Long organizationId, int page, int pageSize) {
        requirePermission(operatorId, "IAM_USER_LIST");
        return listUsers(keyword, type, status, organizationId, page, pageSize);
    }
    public List<Role> listRoles(Long operatorId) {
        requirePermission(operatorId, "IAM_ROLE_READ");
        return listRoles();
    }
    public List<Permission> listPermissions(Long operatorId) {
        requirePermission(operatorId, "IAM_PERMISSION_READ");
        return listPermissions();
    }
    private void requirePermission(Long operatorId, String code) {
        if (!decisions.isAllowed(operatorId, com.lingdong.learning.permission.domain.PermissionClient.WEB, code))
            throw new com.lingdong.learning.common.security.SystemOperationAccessDeniedException("当前账号无IAM读取权限");
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("关键字长度不能超过 64 个字符");
        }
        return normalized;
    }
}
