package com.lingdong.learning.growthpoint.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 成长复盘每日趋势持久化投影。 */
public record GrowthReviewDailyTrendRow(
        LocalDate trendDate,
        Integer taskTotalCount,
        Integer completedCount,
        Integer inProgressCount,
        Integer pendingOptimizationCount,
        BigDecimal completionRate,
        Long earnedPoints,
        Integer pauseCount
) {
}
