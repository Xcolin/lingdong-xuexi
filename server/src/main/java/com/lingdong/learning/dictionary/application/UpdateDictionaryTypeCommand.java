package com.lingdong.learning.dictionary.application;

import com.lingdong.learning.dictionary.domain.DictionaryStatus;

/** Complete mutable state of a dictionary type; codes remain stable after creation. */
public record UpdateDictionaryTypeCommand(
        Long operatorId,
        Long typeId,
        String name,
        Integer sortOrder,
        DictionaryStatus status
) {
}
