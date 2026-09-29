package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 指定学生和日期范围内的任务状态事实。 */
public record GrowthReviewTaskFactRow(
        Integer taskTotalCount,
        Integer completedCount,
        Integer inProgressCount,
        Integer pendingOptimizationCount,
        Integer exemptedCount
) {
    public static GrowthReviewTaskFactRow empty() {
        return new GrowthReviewTaskFactRow(0, 0, 0, 0, 0);
    }
}
