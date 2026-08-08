package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 成长复盘逻辑记录的最小持久化视图。 */
public record GrowthReviewRow(Long id, Long currentSnapshotId) {
}
