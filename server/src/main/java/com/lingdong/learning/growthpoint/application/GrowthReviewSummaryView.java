package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成长复盘列表摘要。 */
public record GrowthReviewSummaryView(
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
        LocalDateTime generatedAt
) {
}
