package com.lingdong.learning.permission.application;

import com.lingdong.learning.permission.domain.Permission;
import com.lingdong.learning.permission.domain.PermissionEffect;
import com.lingdong.learning.permission.domain.PermissionStatus;
import com.lingdong.learning.permission.infrastructure.persistence.PermissionMapper;
import com.lingdong.learning.permission.infrastructure.persistence.UserPermissionMapper;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.User;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

/** Resolves explicit denial before role grants and user-level supplemental allows. */
@Service
public class PermissionDecisionService {
    private final PermissionMapper permissionMapper;
    private final UserPermissionMapper userPermissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserMapper userMapper;

    public PermissionDecisionService(
            PermissionMapper permissionMapper,
            UserPermissionMapper userPermissionMapper,
            UserRoleMapper userRoleMapper,
            UserMapper userMapper
    ) {
        this.permissionMapper = permissionMapper;
        this.userPermissionMapper = userPermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userMapper = userMapper;
    }

    public boolean isAllowed(Long userId, String permissionCode) {
        if (userId == null || permissionCode == null) {
            return false;
        }
        User user = userMapper.findById(userId);
        if (user == null || user.status() != UserStatus.ENABLED) {
            return false;
        }

        Permission permission = permissionMapper.findByCode(permissionCode);
        if (permission == null || permission.status() != PermissionStatus.ENABLED) {
            return false;
        }
        PermissionEffect effect = userPermissionMapper.findEffect(userId, permission.id());
        if (effect == PermissionEffect.DENY) {
            return false;
        }
        if (userRoleMapper.hasPermissionViaRole(userId, permission.id())) {
            return true;
        }
        return effect == PermissionEffect.ALLOW;
    }
}
