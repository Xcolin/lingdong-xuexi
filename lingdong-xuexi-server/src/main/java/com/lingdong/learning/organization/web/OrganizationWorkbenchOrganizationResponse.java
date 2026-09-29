package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.application.OrganizationWorkbenchOrganization;

/** 机构工作台中的直接管理组织响应。 */
public record OrganizationWorkbenchOrganizationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String typeCode
) {
    static OrganizationWorkbenchOrganizationResponse from(OrganizationWorkbenchOrganization organization) {
        return new OrganizationWorkbenchOrganizationResponse(
                organization.id(), organization.name(), organization.typeCode());
    }
}
