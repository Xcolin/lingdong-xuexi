package com.lingdong.learning.cache.web;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 直接缓存操作请求。 */
public record ExecuteCacheOperationRequest(
        @NotNull CacheDomain cacheDomain,
        @NotNull CacheOperationType operationType,
        @NotBlank @Size(max = 1000) String impactDescription
) {
}
