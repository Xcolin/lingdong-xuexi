package com.lingdong.learning.growthpoint.infrastructure.persistence;

/** 当前成长复盘快照的幂等判断视图。 */
public record GrowthReviewSnapshotRow(Long id, Integer contentVersion, String factFingerprint) {
}
