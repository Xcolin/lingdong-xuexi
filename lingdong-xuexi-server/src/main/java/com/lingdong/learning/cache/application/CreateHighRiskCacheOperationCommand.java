package com.lingdong.learning.cache.application;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;

/** 必须由系统审核员审批的高风险缓存操作请求。 */
public record CreateHighRiskCacheOperationCommand(
        Long submitterId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        String title,
        String description,
        boolean confirmed
) {
}
