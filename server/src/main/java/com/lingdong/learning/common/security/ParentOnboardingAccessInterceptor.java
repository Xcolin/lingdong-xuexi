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
    private final ParentPhoneAuthenticationService authenticationService;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public ParentOnboardingAccessInterceptor(
            ParentPhoneAuthenticationService authenticationService,
            SecurityErrorResponseWriter errorResponseWriter
    ) {
        this.authenticationService = authenticationService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser currentUser)
                || !currentUser.roleCodes().contains("PARENT") || isPrerequisitePath(request.getRequestURI())) {
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

    private boolean isPrerequisitePath(String requestUri) {
        return requestUri.startsWith("/api/v1/auth/")
                || requestUri.startsWith("/api/v1/parent-onboarding/")
                || requestUri.startsWith("/api/v1/public/")
                || requestUri.equals("/api/v1/health");
    }
}
