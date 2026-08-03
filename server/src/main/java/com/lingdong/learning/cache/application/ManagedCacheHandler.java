package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;

/** Isolates domain-specific cache behavior from the cache-management use case. */
public interface ManagedCacheHandler {
    CacheDomain domain();

    void clear();

    void refresh();
}
