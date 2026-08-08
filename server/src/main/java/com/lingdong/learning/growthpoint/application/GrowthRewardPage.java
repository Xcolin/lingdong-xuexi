package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReward;

import java.util.List;

/** 家庭奖励受控分页结果。 */
public record GrowthRewardPage(
        List<GrowthReward> items,
        int page,
        int pageSize,
        long total
) {
}
