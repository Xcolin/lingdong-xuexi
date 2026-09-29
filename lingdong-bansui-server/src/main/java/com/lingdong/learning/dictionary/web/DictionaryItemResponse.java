package com.lingdong.learning.dictionary.web;

import com.lingdong.learning.dictionary.domain.DictionaryItem;

/** 字典项响应，包含停用历史项和当前默认标记。 */
public record DictionaryItemResponse(
        String id,
        String typeId,
        String code,
        String name,
        int sortOrder,
        boolean defaultItem,
        String status
) {
    static DictionaryItemResponse from(DictionaryItem item) {
        return new DictionaryItemResponse(
                item.id().toString(), item.typeId().toString(), item.code(), item.name(),
                item.sortOrder(), item.defaultItem(), item.status().name());
    }
}

