package com.lingdong.learning.common.security;

import com.lingdong.learning.auth.application.AuthenticatedUser;
import com.lingdong.learning.datascope.application.OrganizationDataScopeService;
import com.lingdong.learning.organization.application.OrganizationOperationalStatusService;
import com.lingdong.learning.organization.infrastructure.persistence.OrganizationMapper;
import com.lingdong.learning.user.infrastructure.persistence.UserOrganizationMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import java.io.IOException;

@Component
public class SelectedOrganizationInterceptor implements HandlerInterceptor {
    private final OrganizationMapper organizations;
    private final UserOrganizationMapper memberships;
    private final OrganizationDataScopeService scopes;
    private final SecurityErrorResponseWriter errors;
    public SelectedOrganizationInterceptor(OrganizationMapper organizations, UserOrganizationMapper memberships,
            OrganizationDataScopeService scopes, SecurityErrorResponseWriter errors) {
        this.organizations=organizations; this.memberships=memberships; this.scopes=scopes; this.errors=errors;
    }
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        request.removeAttribute(SelectedOrganizationContext.ATTRIBUTE);
        String path=request.getRequestURI();
        if(path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/organization-onboarding/")
                || path.startsWith("/api/v1/parent-onboarding/") || path.startsWith("/api/v1/public/")) return true;
        String header=request.getHeader("X-Organization-Id");
        if(header==null) return true;
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null || !(authentication.getPrincipal() instanceof AuthenticatedUser actor)) return reject(request,response);
        Long id;
        try { if(!header.matches("[1-9][0-9]{0,18}")) return reject(request,response); id=Long.valueOf(header); }
        catch(NumberFormatException exception) { return reject(request,response); }
        var organization=organizations.findById(id);
        if(!OrganizationOperationalStatusService.isOperational(organization)
                || (!memberships.exists(actor.userId(),id) && !scopes.resolve(actor.userId()).allows(organization))) return reject(request,response);
        request.setAttribute(SelectedOrganizationContext.ATTRIBUTE,organization);
        return true;
    }
    private boolean reject(HttpServletRequest request,HttpServletResponse response) throws IOException {
        errors.write(request,response,403,"ORGANIZATION_CONTEXT_FORBIDDEN","当前组织不可访问");return false;
    }
}
