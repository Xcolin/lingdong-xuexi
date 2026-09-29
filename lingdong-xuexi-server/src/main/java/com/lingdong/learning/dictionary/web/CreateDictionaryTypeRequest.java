package com.lingdong.learning.dictionary.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建普通字典类型的 Web 请求。 */
public record CreateDictionaryTypeRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 50) String name,
        @Min(0) Integer sortOrder
) {
}

