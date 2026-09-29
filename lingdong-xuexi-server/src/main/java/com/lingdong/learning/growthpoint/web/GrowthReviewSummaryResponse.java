package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewSummaryView;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成长复盘列表摘要响应。 */
public record GrowthReviewSummaryResponse(
        String reviewId,
        String studentId,
        String studentName,
        GrowthReviewPeriodType periodType,
        LocalDate periodStart,
        LocalDate periodEnd,
        String snapshotId,
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
    public static GrowthReviewSummaryResponse from(GrowthReviewSummaryView view) {
        return new GrowthReviewSummaryResponse(
                view.reviewId().toString(), view.studentId().toString(), view.studentName(),
                view.periodType(), view.periodStart(), view.periodEnd(),
                view.snapshotId().toString(), view.contentVersion(), view.taskTotalCount(),
                view.completedCount(), view.inProgressCount(), view.pendingOptimizationCount(),
                view.exemptedCount(), view.completionRate(), view.earnedPoints(),
                view.pauseCount(), view.generatedAt());
    }
}
