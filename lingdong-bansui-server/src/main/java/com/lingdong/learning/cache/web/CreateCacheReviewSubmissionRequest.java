package com.lingdong.learning.cache.web;

import com.lingdong.learning.cache.domain.CacheDomain;
import com.lingdong.learning.cache.domain.CacheOperationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 创建并提交高风险缓存审核任务的请求。 */
public record CreateCacheReviewSubmissionRequest(
        @NotNull CacheDomain cacheDomain,
        @NotNull CacheOperationType operationType,
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 1000) String description,
        boolean confirmed
) {
}
