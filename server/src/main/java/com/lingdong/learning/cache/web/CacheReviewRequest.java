package com.lingdong.learning.cache.web;

import jakarta.validation.constraints.Size;

/** 高风险缓存任务审核意见。 */
public record CacheReviewRequest(
        @Size(max = 1000) String comment
) {
}
