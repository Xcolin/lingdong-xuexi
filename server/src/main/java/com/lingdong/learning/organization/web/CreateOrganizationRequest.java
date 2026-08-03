package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 创建区域、学校等组织节点的 HTTP 请求。 */
public record CreateOrganizationRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 32) String typeCode,
        @Positive Long parentId,
        @Min(0) Integer sortOrder
) { }
