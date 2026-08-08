package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 成长复盘分类统计持久化投影。 */
public record GrowthReviewCategoryRow(
        String categoryCode,
        Integer taskCount,
        Integer completedCount
) {
}
