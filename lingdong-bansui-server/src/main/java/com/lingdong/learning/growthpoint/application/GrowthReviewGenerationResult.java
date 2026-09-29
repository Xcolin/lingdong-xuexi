package com.lingdong.learning.growthpoint.application;

/** 成长复盘生成结果；未产生新版本时返回当前快照。 */
public record GrowthReviewGenerationResult(
        Long reviewId,
        Long snapshotId,
        int contentVersion,
        boolean created
) {
}
