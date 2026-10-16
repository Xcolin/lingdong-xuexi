package com.lingdong.learning.common.security;

import com.lingdong.learning.organization.domain.Organization;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Only an interceptor-validated request attribute is visible to business queries. */
public final class SelectedOrganizationContext {
    public static final String ATTRIBUTE = SelectedOrganizationContext.class.getName();
    private SelectedOrganizationContext() {}
    public static Organization organization() {
        var attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? (Organization) servlet.getRequest().getAttribute(ATTRIBUTE) : null;
    }
    public static Long organizationId() {
        Organization organization = organization();
        return organization == null ? null : organization.id();
    }
}
