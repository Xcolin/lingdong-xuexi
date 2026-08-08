package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthRewardExchange;

import java.util.List;

/** 奖励兑换记录受控分页结果。 */
public record GrowthRewardExchangePage(
        List<GrowthRewardExchange> items,
        int page,
        int pageSize,
        long total
) {
}
