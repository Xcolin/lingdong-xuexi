package com.lingdong.learning.growthpoint.application;

/** 成长复盘分类统计。 */
public record GrowthReviewCategoryView(
        String categoryCode,
        int taskCount,
        int completedCount
) {
}
