package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthRewardExchangePage;

import java.util.List;

/** 奖励兑换记录分页响应。 */
public record GrowthRewardExchangePageResponse(
        List<GrowthRewardExchangeResponse> items,
        int page,
        int pageSize,
        long total
) {
    static GrowthRewardExchangePageResponse from(GrowthRewardExchangePage page) {
        return new GrowthRewardExchangePageResponse(
                page.items().stream().map(GrowthRewardExchangeResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
