package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.domain.Organization;
import com.lingdong.learning.organization.domain.OrganizationStatus;

import java.time.LocalDateTime;
import java.util.List;

/** 组织树节点响应，不暴露内部父级范围键。 */
public record OrganizationTreeNodeResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        String code,
        String name,
        String typeCode,
        String path,
        Integer sortOrder,
        OrganizationStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrganizationTreeNodeResponse> children
) {
    static OrganizationTreeNodeResponse from(Organization organization) {
        return from(organization, List.of());
    }

    static OrganizationTreeNodeResponse from(
            Organization organization,
            List<OrganizationTreeNodeResponse> children
    ) {
        return new OrganizationTreeNodeResponse(
                organization.id(), organization.parentId(), organization.code(), organization.name(), organization.typeCode(),
                organization.path(), organization.sortOrder(), organization.status(), organization.createdAt(), organization.updatedAt(),
                children
        );
    }
}
