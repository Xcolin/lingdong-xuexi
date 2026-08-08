package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 成长复盘分类统计写入对象。 */
public record GrowthReviewCategoryWrite(
        Long id,
        Long snapshotId,
        String categoryCode,
        int taskCount,
        int completedCount
) {
}
