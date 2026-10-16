package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.auth.application.ParentAuthState;
import com.lingdong.learning.auth.application.ParentPhoneAuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/** 在家长协议和首次引导完成前阻断直接访问普通业务接口。 */
@Component
public class ParentOnboardingAccessInterceptor implements HandlerInterceptor {
    private final com.lingdong.learning.user.infrastructure.persistence.UserMapper users;
    private final com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper profiles;
    private final ParentPhoneAuthenticationService authenticationService;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public ParentOnboardingAccessInterceptor(
            ParentPhoneAuthenticationService authenticationService,
            SecurityErrorResponseWriter errorResponseWriter,
            com.lingdong.learning.user.infrastructure.persistence.UserMapper users,
            com.lingdong.learning.auth.infrastructure.persistence.ParentAuthenticationMapper profiles
    ) {
        this.authenticationService = authenticationService;
        this.errorResponseWriter = errorResponseWriter; this.users=users;this.profiles=profiles;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser currentUser)
                || !isActualParent(currentUser) || isPrerequisitePath(request.getRequestURI())
                || !requiresFamilyPrerequisites(currentUser,request)) {
            return true;
        }

        ParentAuthState state = authenticationService.getParentAccessState(currentUser.userId());
        if (state == null) {
            return true;
        }
        if (state.agreementAcceptanceRequired()) {
            errorResponseWriter.write(request, response, HttpStatus.PRECONDITION_REQUIRED.value(),
                    "PARENT_AGREEMENT_REQUIRED", "请先同意当前用户协议");
            return false;
        }
        if (state.onboardingRequired()) {
            errorResponseWriter.write(request, response, HttpStatus.PRECONDITION_REQUIRED.value(),
                    "PARENT_ONBOARDING_REQUIRED", "请先完成家长首次引导");
            return false;
        }
        return true;
    }

    private boolean isActualParent(AuthenticatedUser actor) {
        var user=actor.userId()==null?null:users.findById(actor.userId());
        return user!=null && user.status()==com.lingdong.learning.user.domain.UserStatus.ENABLED
                && user.type()!=com.lingdong.learning.user.domain.UserType.STUDENT
                && profiles.findProfileByUserId(user.id())!=null;
    }
    private boolean requiresFamilyPrerequisites(AuthenticatedUser actor,HttpServletRequest request) {
        var user=users.findById(actor.userId());
        if(user.type()==com.lingdong.learning.user.domain.UserType.FAMILY)return true;
        Object selected=request.getAttribute(SelectedOrganizationContext.ATTRIBUTE);
        if(selected instanceof com.lingdong.learning.organization.domain.Organization org && "FAMILY".equals(org.typeCode()))return true;
        String uri=request.getRequestURI();
        return uri.startsWith("/api/v1/parents/") || uri.startsWith("/api/v1/parent-") || "FAMILY".equals(request.getParameter("sourceType"));
    }
    private boolean isPrerequisitePath(String requestUri) {
        return requestUri.startsWith("/api/v1/auth/")
                || requestUri.startsWith("/api/v1/organization-onboarding/")
                || requestUri.startsWith("/api/v1/parent-onboarding/")
                || requestUri.startsWith("/api/v1/public/")
                || requestUri.equals("/api/v1/health");
    }
}
