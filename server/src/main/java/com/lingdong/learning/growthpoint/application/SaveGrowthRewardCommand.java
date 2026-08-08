package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;

import java.time.LocalDateTime;

/** 新增或修改家庭奖励所需的业务字段。 */
public record SaveGrowthRewardCommand(
        String rewardName,
        Long requiredPoints,
        String description,
        LocalDateTime expiresAt,
        GrowthRewardStatus status
) {
}
