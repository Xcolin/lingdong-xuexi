package com.lingdong.learning.growthpoint.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 成长复盘每日趋势写入对象。 */
public record GrowthReviewDailyTrendWrite(
        Long id,
        Long snapshotId,
        LocalDate trendDate,
        int taskTotalCount,
        int completedCount,
        int inProgressCount,
        int pendingOptimizationCount,
        BigDecimal completionRate,
        long earnedPoints,
        int pauseCount
) {
}
