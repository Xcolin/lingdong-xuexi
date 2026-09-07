package com.lingdong.learning.cache.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperation;
import com.lingdong.learning.cache.domain.CacheOperationStatus;
import com.lingdong.learning.cache.domain.CacheOperationType;

import java.time.LocalDateTime;

/** 缓存操作台账响应，雪花标识统一按字符串输出。 */
public record CacheOperationResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String code,
        @JsonSerialize(using = ToStringSerializer.class) Long taskId,
        CacheDomain cacheDomain,
        CacheOperationType operationType,
        CacheOperationStatus status,
        String impactDescription,
        @JsonSerialize(using = ToStringSerializer.class) Long requestedBy,
        @JsonSerialize(using = ToStringSerializer.class) Long executedBy,
        String failureMessage,
        LocalDateTime executedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CacheOperationResponse from(CacheOperation operation) {
        return new CacheOperationResponse(
                operation.id(),
                operation.code(),
                operation.taskId(),
                operation.domain(),
                operation.operationType(),
                operation.status(),
                operation.impactDescription(),
                operation.requestedBy(),
                operation.executedBy(),
                operation.failureMessage(),
                operation.executedAt(),
                operation.createdAt(),
                operation.updatedAt()
        );
    }
}
