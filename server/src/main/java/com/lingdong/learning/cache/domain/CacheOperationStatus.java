package com.lingdong.learning.cache.domain;

/** 缓存管理请求的持久化执行状态。 */
public enum CacheOperationStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    REJECTED
}
