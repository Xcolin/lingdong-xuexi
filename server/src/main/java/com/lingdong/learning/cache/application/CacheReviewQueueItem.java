package com.lingdong.learning.cache.application;

import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationStatus;
import com.lingdong.learning.cache.domain.CacheOperationType;

import java.time.LocalDateTime;

/** 高风险缓存任务审核队列中的只读摘要。 */
public record CacheReviewQueueItem(
        Long operationId,
        Long taskId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        CacheOperationStatus operationStatus,
        String impactDescription,
        SystemTaskStatus taskStatus,
        String taskTitle,
        Long submittedBy,
        LocalDateTime submittedAt
) {
}
