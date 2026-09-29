package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.SaveGrowthRewardCommand;
import com.lingdong.learning.growthpoint.domain.GrowthRewardStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** 家庭奖励新增和修改请求。 */
public record SaveGrowthRewardRequest(
        @NotBlank @Size(max = 30) String rewardName,
        @NotNull @Positive Long requiredPoints,
        @Size(max = 200) String description,
        LocalDateTime expiresAt,
        @NotNull GrowthRewardStatus status
) {
    SaveGrowthRewardCommand toCommand() {
        return new SaveGrowthRewardCommand(
                rewardName, requiredPoints, description, expiresAt, status);
    }
}
