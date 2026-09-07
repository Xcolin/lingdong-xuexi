package com.lingdong.learning.organization.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 班级名称和排序编辑请求。 */
public record UpdateClassRequest(
        @NotBlank @Size(max = 50) String name,
        @Min(0) Integer sortOrder,
        @NotNull @Positive Integer versionNo
) {
}
