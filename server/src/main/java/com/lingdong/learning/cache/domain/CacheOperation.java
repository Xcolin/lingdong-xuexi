package com.lingdong.learning.cache.domain;

import java.time.LocalDateTime;

/** 缓存操作请求及其执行结果的不可变审计记录。 */
public record CacheOperation(
        Long id,
        String code,
        Long taskId,
        CacheDomain domain,
        CacheOperationType operationType,
        CacheOperationStatus status,
        String impactDescription,
        Long requestedBy,
        Long executedBy,
        String failureMessage,
        LocalDateTime executedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CacheOperation pending(
            Long id,
            String code,
            Long taskId,
            CacheDomain domain,
            CacheOperationType operationType,
            String impactDescription,
            Long requestedBy
    ) {
        return new CacheOperation(
                id,
                code,
                taskId,
                domain,
                operationType,
                CacheOperationStatus.PENDING,
                impactDescription,
                requestedBy,
                null,
                null,
                null,
                null,
                null
        );
    }
}
