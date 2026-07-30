package com.lingdong.learning.dictionary.infrastructure.cache;

import com.lingdong.learning.dictionary.domain.DictionaryItem;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryItemMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/** Caches enabled selectable values by dictionary type code. */
@Component
public class DictionaryItemCache {
    public static final String CACHE_NAME = "dictionary-items";

    private final DictionaryItemMapper dictionaryItemMapper;

    public DictionaryItemCache(DictionaryItemMapper dictionaryItemMapper) {
        this.dictionaryItemMapper = dictionaryItemMapper;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#p0")
    public List<DictionaryItem> findEnabledItems(String typeCode) {
        return List.copyOf(dictionaryItemMapper.findEnabledByTypeCode(typeCode));
    }

    /** Evicts only one type after a successful write, keeping unrelated dictionary caches warm. */
    @CacheEvict(cacheNames = CACHE_NAME, key = "#p0")
    public void evict(String typeCode) {
        // Cache eviction is performed by the Spring proxy after this no-op method returns.
    }
}
