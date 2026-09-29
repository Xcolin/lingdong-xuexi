package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;

/** 无需系统任务审批的缓存域直接操作请求。 */
public record ExecuteCacheOperationCommand(
        Long operatorId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        String impactDescription
) {
}
