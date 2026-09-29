package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.application.OrganizationWorkbenchContext;

import java.util.List;

/** 机构管理员小程序工作台最小上下文响应。 */
public record OrganizationWorkbenchContextResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long userId,
        String username,
        String displayName,
        List<String> permissionCodes,
        List<OrganizationWorkbenchOrganizationResponse> organizations
) {
    static OrganizationWorkbenchContextResponse from(OrganizationWorkbenchContext context) {
        return new OrganizationWorkbenchContextResponse(
                context.userId(),
                context.username(),
                context.displayName(),
                context.permissionCodes(),
                context.organizations().stream()
                        .map(OrganizationWorkbenchOrganizationResponse::from)
                        .toList());
    }
}
