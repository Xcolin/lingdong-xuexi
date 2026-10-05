package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 组织名称和排序的低风险编辑请求。 */
public record UpdateOrganizationRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(0) Integer sortOrder,
        @NotNull @Positive Integer versionNo,
        @Size(max = 32) String adminDivisionCode
) {
}
