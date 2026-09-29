package com.lingdong.learning.growthpoint.domain;

import java.time.LocalDateTime;

/** 按学生隔离的家庭奖励配置。 */
public record GrowthReward(
        Long id,
        Long studentId,
        Long createdByParentId,
        String rewardName,
        Long requiredPoints,
        String description,
        LocalDateTime expiresAt,
        GrowthRewardStatus status,
        Integer versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt
) {
}
