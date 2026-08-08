package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 成长复盘任务分类事实。 */
public record GrowthReviewCategoryFactRow(
        String categoryCode,
        Integer taskCount,
        Integer completedCount
) {
}
