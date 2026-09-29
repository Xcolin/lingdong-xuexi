package com.lingdong.learning.organization.web;

import com.lingdong.learning.organization.domain.OrganizationChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 高风险组织变更 HTTP 申请。 */
public record CreateOrganizationChangeRequest(
        @NotNull @Positive Long organizationId,
        @NotNull OrganizationChangeType changeType,
        @Positive Long targetParentId,
        @NotNull @Positive Integer expectedVersion,
        @NotBlank @Size(max = 500) String reason
) {
}
