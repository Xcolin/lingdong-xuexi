package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthReviewGenerationSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 不可变成长复盘快照写入对象。 */
public record GrowthReviewSnapshotWrite(
        Long id,
        Long reviewId,
        int contentVersion,
        int taskTotalCount,
        int completedCount,
        int inProgressCount,
        int pendingOptimizationCount,
        int exemptedCount,
        BigDecimal completionRate,
        long earnedPoints,
        int pauseCount,
        GrowthReviewGenerationSource generationSource,
        String factFingerprint,
        LocalDateTime dataCutoffAt,
        LocalDateTime generatedAt
) {
}
