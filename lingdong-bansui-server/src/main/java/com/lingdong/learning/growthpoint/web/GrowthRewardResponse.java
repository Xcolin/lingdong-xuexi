package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.domain.GrowthReward;
import com.lingdong.learning.growthpoint.domain.GrowthRewardCategory;
import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;

import java.time.LocalDateTime;

/** 家庭奖励响应，不暴露内部乐观锁和删除字段。 */
public record GrowthRewardResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String rewardName,
        long requiredPoints,
        GrowthRewardCategory category,
        int stockQuantity,
        String imageUrl,
        String description,
        LocalDateTime expiresAt,
        GrowthRewardStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static GrowthRewardResponse from(GrowthReward reward) {
        return new GrowthRewardResponse(
                reward.id(), reward.studentId(), reward.rewardName(), reward.requiredPoints(),
                reward.category(), reward.stockQuantity() == null ? 0 : reward.stockQuantity(),
                reward.imageUrl(), reward.description(), reward.expiresAt(), reward.status(),
                reward.createdAt(), reward.updatedAt());
    }
}
