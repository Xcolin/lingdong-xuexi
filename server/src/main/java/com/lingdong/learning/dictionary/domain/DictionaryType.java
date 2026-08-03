package com.lingdong.learning.dictionary.domain;

import java.time.LocalDateTime;

/** Immutable configurable category that groups reusable dictionary items. */
public record DictionaryType(
        Long id,
        String code,
        String name,
        DictionaryStatus status,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DictionaryType enabled(Long id, String code, String name, int sortOrder) {
        return new DictionaryType(id, code, name, DictionaryStatus.ENABLED, sortOrder, null, null);
    }
}
