package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewCategoryView;
import com.lingdong.learning.growthpoint.application.GrowthReviewDailyTrendView;
import com.lingdong.learning.growthpoint.application.GrowthReviewDetailView;
import com.lingdong.learning.growthpoint.domain.GrowthReviewPeriodType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 当前成长复盘快照详情响应。 */
public record GrowthReviewDetailResponse(
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
        LocalDateTime dataCutoffAt,
        LocalDateTime generatedAt,
        List<CategoryResponse> categories,
        List<DailyTrendResponse> dailyTrends,
        List<GrowthReviewSupplementResponse> supplements
) {
    public static GrowthReviewDetailResponse from(GrowthReviewDetailView view) {
        return new GrowthReviewDetailResponse(
                view.reviewId().toString(), view.studentId().toString(), view.studentName(),
                view.periodType(), view.periodStart(), view.periodEnd(),
                view.snapshotId().toString(), view.contentVersion(), view.taskTotalCount(),
                view.completedCount(), view.inProgressCount(), view.pendingOptimizationCount(),
                view.exemptedCount(), view.completionRate(), view.earnedPoints(), view.pauseCount(),
                view.dataCutoffAt(), view.generatedAt(),
                view.categories().stream().map(CategoryResponse::from).toList(),
                view.dailyTrends().stream().map(DailyTrendResponse::from).toList(),
                view.supplements().stream().map(GrowthReviewSupplementResponse::from).toList());
    }

    /** 分类任务完成统计。 */
    public record CategoryResponse(String categoryCode, int taskCount, int completedCount) {
        static CategoryResponse from(GrowthReviewCategoryView view) {
            return new CategoryResponse(
                    view.categoryCode(), view.taskCount(), view.completedCount());
        }
    }

    /** 单日任务、积分与暂停趋势。 */
    public record DailyTrendResponse(
            LocalDate trendDate,
            int taskTotalCount,
            int completedCount,
            int inProgressCount,
            int pendingOptimizationCount,
            BigDecimal completionRate,
            long earnedPoints,
            int pauseCount
    ) {
        static DailyTrendResponse from(GrowthReviewDailyTrendView view) {
            return new DailyTrendResponse(
                    view.trendDate(), view.taskTotalCount(), view.completedCount(),
                    view.inProgressCount(), view.pendingOptimizationCount(),
                    view.completionRate(), view.earnedPoints(), view.pauseCount());
        }
    }
}
