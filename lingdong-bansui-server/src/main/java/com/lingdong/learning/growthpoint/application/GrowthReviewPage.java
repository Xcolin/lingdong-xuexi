package com.lingdong.learning.growthpoint.application;

import java.util.List;

/** 成长复盘分页结果。 */
public record GrowthReviewPage(
        List<GrowthReviewSummaryView> items,
        int page,
        int pageSize,
        long total
) {
    public GrowthReviewPage {
        items = List.copyOf(items);
    }
}
