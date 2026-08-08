package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 当前成长复盘快照详情及其追加补录。 */
public record GrowthReviewDetailView(
        Long reviewId,
        Long studentId,
        String studentName,
        GrowthReviewPeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long snapshotId,
        int contentVersion,
        int taskTotalCount,
        int completedCount,
        int inProgressCount,
        int pendingOptimizationCount,
        int exemptedCount,
        BigDecimal completionRate,
        long earnedPoints,
        int pauseCount,
        LocalDateTime dataCutoffAt,
        LocalDateTime generatedAt,
        List<GrowthReviewCategoryView> categories,
        List<GrowthReviewDailyTrendView> dailyTrends,
        List<GrowthReviewSupplementView> supplements
) {
    public GrowthReviewDetailView {
        categories = List.copyOf(categories);
        dailyTrends = List.copyOf(dailyTrends);
        supplements = List.copyOf(supplements);
    }
}
