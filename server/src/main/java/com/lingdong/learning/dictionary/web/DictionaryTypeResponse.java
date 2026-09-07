package com.lingdong.learning.dictionary.web;

import com.lingdong.learning.dictionary.domain.DictionaryType;

/** 字典类型响应，雪花标识始终按字符串传输。 */
public record DictionaryTypeResponse(
        String id,
        String code,
        String name,
        String status,
        int sortOrder
) {
    static DictionaryTypeResponse from(DictionaryType type) {
        return new DictionaryTypeResponse(
                type.id().toString(), type.code(), type.name(), type.status().name(), type.sortOrder());
    }
}

