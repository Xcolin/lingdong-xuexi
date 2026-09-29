package com.lingdong.learning.dictionary.web;

import com.lingdong.learning.dictionary.domain.DictionaryStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 更新字典类型可变属性的 Web 请求。 */
public record UpdateDictionaryTypeRequest(
        @NotBlank @Size(max = 50) String name,
        @Min(0) Integer sortOrder,
        @NotNull DictionaryStatus status
) {
}

