package com.lingdong.learning.dictionary.application;

import com.lingdong.learning.dictionary.domain.DictionaryStatus;

/** Complete mutable state of a dictionary item; codes remain stable after creation. */
public record UpdateDictionaryItemCommand(
        Long operatorId,
        Long itemId,
        String name,
        Integer sortOrder,
        boolean defaultItem,
        DictionaryStatus status
) {
}
