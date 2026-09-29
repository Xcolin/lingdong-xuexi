package com.lingdong.learning.organization.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.organization.domain.OrganizationStatus;
import com.lingdong.learning.organization.domain.OrganizationType;

import java.time.LocalDateTime;

/** 组织类型目录响应，雪花主键以字符串返回。 */
public record OrganizationTypeResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        String name,
        boolean builtIn,
        OrganizationStatus status,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static OrganizationTypeResponse from(OrganizationType organizationType) {
        return new OrganizationTypeResponse(
                organizationType.id(), organizationType.code(), organizationType.name(), organizationType.builtIn(),
                organizationType.status(), organizationType.sortOrder(), organizationType.createdAt(), organizationType.updatedAt()
        );
    }
}
