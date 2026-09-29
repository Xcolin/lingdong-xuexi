package com.lingdong.learning.dictionary.domain;

import java.time.LocalDateTime;

/** Immutable selectable value belonging to one dictionary type. */
public record DictionaryItem(
        Long id,
        Long typeId,
        String code,
        String name,
        Integer sortOrder,
        boolean defaultItem,
        DictionaryStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DictionaryItem enabled(
            Long id,
            Long typeId,
            String code,
            String name,
            int sortOrder,
            boolean defaultItem
    ) {
        return new DictionaryItem(
                id,
                typeId,
                code,
                name,
                sortOrder,
                defaultItem,
                DictionaryStatus.ENABLED,
                null,
                null
        );
    }
}
