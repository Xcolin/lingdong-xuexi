package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 机构管理员新增班级请求。 */
public record CreateClassRequest(
        @NotNull @Positive Long schoolOrganizationId,
        @NotBlank @Size(max = 50) String name,
        @Min(0) Integer sortOrder
) {
}
