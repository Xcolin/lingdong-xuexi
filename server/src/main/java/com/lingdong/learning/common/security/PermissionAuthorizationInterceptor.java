package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.permission.application.PermissionDecisionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** 在进入带权限声明的控制器方法前执行动态 RBAC 决策。 */
@Component
public class PermissionAuthorizationInterceptor implements HandlerInterceptor {
    private final PermissionDecisionService permissionDecisionService;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public PermissionAuthorizationInterceptor(
            PermissionDecisionService permissionDecisionService,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.permissionDecisionService = permissionDecisionService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequirePermission requiredPermission = handlerMethod.getMethodAnnotation(RequirePermission.class);
        if (requiredPermission == null) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser currentUser)) {
            errorResponseWriter.write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                    "AUTH_REQUIRED", "需要有效登录会话");
            return false;
        }
        if (!permissionDecisionService.isAllowed(currentUser.userId(), requiredPermission.value())) {
            errorResponseWriter.write(request, response, HttpServletResponse.SC_FORBIDDEN,
                    "ACCESS_DENIED", "无权执行此操作");
            return false;
        }
        return true;
    }
}
