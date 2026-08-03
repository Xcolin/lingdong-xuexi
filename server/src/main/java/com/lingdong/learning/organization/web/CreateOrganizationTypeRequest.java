package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建自定义组织类型的 HTTP 请求。 */
public record CreateOrganizationTypeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 32) String name,
        @Min(0) Integer sortOrder
) { }
