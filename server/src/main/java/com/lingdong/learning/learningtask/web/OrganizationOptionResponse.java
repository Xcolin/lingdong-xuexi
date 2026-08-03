package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.application.OrganizationOption;

/** 任务组织候选响应。 */
public record OrganizationOptionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String name,
        String organizationType,
        @JsonSerialize(using = ToStringSerializer.class) Long parentId,
        String organizationPath
) {
    static OrganizationOptionResponse from(OrganizationOption option) {
        return new OrganizationOptionResponse(
                option.id(), option.name(), option.organizationType(), option.parentId(), option.organizationPath());
    }
}
