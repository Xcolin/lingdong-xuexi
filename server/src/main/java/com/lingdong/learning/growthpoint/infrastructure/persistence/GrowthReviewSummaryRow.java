package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成长复盘列表持久化投影。 */
public record GrowthReviewSummaryRow(
        Long reviewId,
        Long studentId,
        String studentName,
        GrowthReviewPeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        Long snapshotId,
        Integer contentVersion,
        Integer taskTotalCount,
        Integer completedCount,
        Integer inProgressCount,
        Integer pendingOptimizationCount,
        Integer exemptedCount,
        BigDecimal completionRate,
        Long earnedPoints,
        Integer pauseCount,
        LocalDateTime generatedAt
) {
}
