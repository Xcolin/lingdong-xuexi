package com.lingdong.learning.growthpoint.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 成长复盘每日趋势。 */
public record GrowthReviewDailyTrendView(
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
