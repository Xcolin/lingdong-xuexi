package com.lingdong.learning.growthpoint.domain;
import java.time.LocalDateTime;

/** 本人对孩子的主动订阅偏好，版本用于后续待发送任务资格复核。 */
public record GrowthReviewSubscription(Long id, Long parentUserId, Long studentId,
        boolean enabled, long version, LocalDateTime updatedAt) { }
