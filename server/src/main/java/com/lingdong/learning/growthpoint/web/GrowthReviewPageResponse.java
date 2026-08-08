package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewPage;

import java.util.List;

/** 成长复盘分页响应。 */
public record GrowthReviewPageResponse(
        List<GrowthReviewSummaryResponse> items,
        int page,
        int pageSize,
        long total
) {
    public static GrowthReviewPageResponse from(GrowthReviewPage page) {
        return new GrowthReviewPageResponse(
                page.items().stream().map(GrowthReviewSummaryResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
