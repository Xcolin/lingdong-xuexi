package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;

/** Direct request for a cache-domain action that is not subject to system-task approval. */
public record ExecuteCacheOperationCommand(
        Long operatorId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        String impactDescription
) {
}
