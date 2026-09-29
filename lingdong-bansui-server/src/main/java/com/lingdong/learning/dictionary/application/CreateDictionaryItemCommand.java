package com.lingdong.learning.dictionary.application;

/** Input for creating one enabled item under an existing dictionary type. */
public record CreateDictionaryItemCommand(
        Long operatorId,
        Long typeId,
        String code,
        String name,
        Integer sortOrder,
        boolean defaultItem
) {
}
