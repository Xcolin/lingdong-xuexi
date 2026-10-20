package com.lingdong.learning.auth.application;

import com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper;
import com.lingdong.learning.common.security.SystemOperationAccessDeniedException;
import com.lingdong.learning.user.domain.UserStatus;
import com.lingdong.learning.user.domain.UserType;
import com.lingdong.learning.user.infrastructure.persistence.UserMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserRoleMapper;
import org.springframework.stereotype.Service;

/** Actual family account identity; action grants and guardian resource scope are checked separately. */
@Service
public class ParentActorAccessService {
    private final UserMapper users;
    private final ParentAuthenticationMapper parents;
    private final UserRoleMapper roles;
    public ParentActorAccessService(UserMapper users, ParentAuthenticationMapper parents, UserRoleMapper roles) {
        this.users = users;
        this.parents = parents;
        this.roles = roles;
    }
    public void require(AuthenticatedUser actor) {
        var user = actor == null || actor.userId() == null ? null : users.findById(actor.userId());
        // 只有家庭账号（FAMILY）与公开注册个人账号（PERSONAL）能持有家长身份，防止其他账号类型冒用家长角色
        if (user == null || (user.type() != UserType.FAMILY && user.type() != UserType.PERSONAL)
                || user.status() != UserStatus.ENABLED
                || parents.findProfileByUserId(user.id()) == null || !roles.hasAnyEnabledRole(user.id())) {
            throw new SystemOperationAccessDeniedException("当前操作要求启用的家庭账号、家长档案及活动角色");
        }
    }
}
