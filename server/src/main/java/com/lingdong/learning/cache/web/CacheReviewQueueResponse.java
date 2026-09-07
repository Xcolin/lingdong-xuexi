package com.lingdong.learning.cache.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.audit.application.SystemTaskStatus;
import com.lingdong.learning.cache.application.CacheReviewQueueItem;
import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationStatus;
import com.lingdong.learning.cache.domain.CacheOperationType;

import java.time.LocalDateTime;

/** 高风险缓存任务审核队列响应。 */
public record CacheReviewQueueResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long operationId,
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        CacheOperationStatus operationStatus,
        String impactDescription,
        SystemTaskStatus taskStatus,
        String taskTitle,
        @JsonSerialize(using = ToStringSerializer.class) Long submittedBy,
        LocalDateTime submittedAt
) {
    public static CacheReviewQueueResponse from(CacheReviewQueueItem item) {
        return new CacheReviewQueueResponse(
                item.operationId(),
                item.taskId(),
                item.cacheDomain(),
                item.operationType(),
                item.operationStatus(),
                item.impactDescription(),
                item.taskStatus(),
                item.taskTitle(),
                item.submittedBy(),
                item.submittedAt()
        );
    }
}
