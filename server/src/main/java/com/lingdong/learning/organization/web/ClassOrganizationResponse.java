package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationEffectiveStatus;
import com.lingdong.learning.organization.domain.OrganizationStatus;

import java.time.LocalDateTime;

/** Web 与小程序共用的班级或学校候选响应。 */
public record ClassOrganizationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        String code,
        String name,
        String typeCode,
        Integer sortOrder,
        OrganizationStatus status,
        OrganizationEffectiveStatus effectiveStatus,
        Integer versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static ClassOrganizationResponse from(Organization organization) {
        return new ClassOrganizationResponse(
                organization.id(), organization.parentId(), organization.code(), organization.name(),
                organization.typeCode(), organization.sortOrder(), organization.status(),
                organization.effectiveStatus(), organization.versionNo(),
                organization.createdAt(), organization.updatedAt());
    }
}
