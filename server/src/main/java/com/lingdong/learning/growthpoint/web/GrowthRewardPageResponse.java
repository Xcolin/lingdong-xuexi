package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthRewardPage;

import java.util.List;

/** 家庭奖励分页响应。 */
public record GrowthRewardPageResponse(
        List<GrowthRewardResponse> items,
        int page,
        int pageSize,
        long total
) {
    static GrowthRewardPageResponse from(GrowthRewardPage page) {
        return new GrowthRewardPageResponse(
                page.items().stream().map(GrowthRewardResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
