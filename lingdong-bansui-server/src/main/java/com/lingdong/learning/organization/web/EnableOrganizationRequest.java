package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 组织节点重新启用请求。 */
public record EnableOrganizationRequest(
        @NotNull @Positive Integer versionNo
) {
}
