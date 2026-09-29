package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;

/** 将各业务域缓存行为与缓存管理用例隔离。 */
public interface ManagedCacheHandler {
    CacheDomain domain();

    void clear();

    void refresh();
}
