package com.lingdong.learning.dictionary.application;

/** Input for creating one ordinary reusable dictionary type. */
public record CreateDictionaryTypeCommand(Long operatorId, String code, String name, Integer sortOrder) {
}
