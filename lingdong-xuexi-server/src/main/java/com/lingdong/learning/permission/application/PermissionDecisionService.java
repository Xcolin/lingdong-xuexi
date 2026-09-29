package com.lingdong.learning.permission.application;

import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionClient;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.RolePermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.UserPermissionMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/** 按账号、客户端和权限目录执行显式拒绝优先的动态权限决策。 */
@Service
public class PermissionDecisionService {
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;

    public PermissionDecisionService(
            PermissionMapper permissionMapper,
            RolePermissionMapper rolePermissionMapper,
            UserPermissionMapper userPermissionMapper,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper
    ) {
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
    }

    public boolean isAllowed(Long userId, PermissionClient requestClient, String permissionCode) {
        if (userId == null || requestClient == null || permissionCode == null) {
            return false;
        }
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) {
            return false;
        }

        Permission permission = permissionMapper.findByCode(permissionCode);
        if (permission == null
                || permission.status() != PermissionStatus.ENABLED
                || !supportsClient(permission.client(), requestClient)) {
            return false;
        }
        PermissionEffect effect = userPermissionMapper.findEffect(userId, permission.id());
        if (rolePermissionMapper.hasEffectForUser(userId, permission.id(), PermissionEffect.DENY)
                || effect == PermissionEffect.DENY) {
            return false;
        }
        if (userRoleMapper.hasPermissionViaRole(userId, permission.id())) {
            return true;
        }
        return effect == PermissionEffect.ALLOW && userRoleMapper.hasAnyEnabledRole(userId);
    }

    /** 返回当前账号在指定前端应用中可实际使用的权限编码，结果按编码稳定排序。 */
    public List<String> findAllowedCodes(Long userId, PermissionClient requestClient) {
        if (userId == null || requestClient == null) {
            return List.of();
        }
        return permissionMapper.findAllowedCodes(userId, requestClient);
    }

    private boolean supportsClient(PermissionClient permissionClient, PermissionClient requestClient) {
        return permissionClient == PermissionClient.BOTH || permissionClient == requestClient;
    }
}
