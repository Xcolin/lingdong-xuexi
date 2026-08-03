package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;

/** Draft request for a cache action that must be approved by a system auditor. */
public record CreateHighRiskCacheOperationCommand(
        Long submitterId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        String title,
        String description,
        boolean confirmed
) {
}
