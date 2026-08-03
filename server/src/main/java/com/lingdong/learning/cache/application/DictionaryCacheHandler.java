package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.dictionary.application.DictionaryQueryService;
import com.lingdong.learning.dictionary.infrastructure.cache.DictionaryItemCache;
import com.lingdong.learning.dictionary.infrastructure.persistence.DictionaryTypeMapper;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/** Clears or warms the cache containing enabled dictionary items. */
@Component
public class DictionaryCacheHandler implements ManagedCacheHandler {
    private final CacheManager cacheManager;
    private final DictionaryTypeMapper dictionaryTypeMapper;
    private final DictionaryQueryService dictionaryQueryService;

    public DictionaryCacheHandler(
            CacheManager cacheManager,
            DictionaryTypeMapper dictionaryTypeMapper,
            DictionaryQueryService dictionaryQueryService
    ) {
        this.cacheManager = cacheManager;
        this.dictionaryTypeMapper = dictionaryTypeMapper;
        this.dictionaryQueryService = dictionaryQueryService;
    }

    @Override
    public CacheDomain domain() {
        return CacheDomain.DICTIONARY;
    }

    @Override
    public void clear() {
        Cache cache = cacheManager.getCache(DictionaryItemCache.CACHE_NAME);
        if (cache == null) {
            throw new IllegalStateException("未注册字典缓存：" + DictionaryItemCache.CACHE_NAME);
        }
        cache.clear();
    }

    @Override
    public void refresh() {
        clear();
        dictionaryTypeMapper.findEnabledCodes().forEach(dictionaryQueryService::findEnabledItems);
    }
}
