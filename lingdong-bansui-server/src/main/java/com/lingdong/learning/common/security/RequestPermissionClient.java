package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.permission.domain.PermissionClient;
import org.springframework.security.core.context.SecurityContextHolder;

/** Keeps legacy internal calls on WEB and evaluates authenticated requests on their actual client. */
public final class RequestPermissionClient {
    private RequestPermissionClient() { }

    public static PermissionClient forOperator(Long operatorId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return PermissionClient.WEB;
        if (!(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || operatorId == null || !operatorId.equals(user.userId()) || user.clientType() == null) {
            throw new SystemOperationAccessDeniedException("操作账号与当前会话不一致");
        }
        return PermissionClient.valueOf(user.clientType().name());
    }
}
