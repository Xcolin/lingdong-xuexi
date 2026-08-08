package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;

import java.time.LocalDateTime;

/** 成长复盘补录持久化投影。 */
public record GrowthReviewSupplementRow(
        Long id,
        Long editorUserId,
        String editorRole,
        GrowthReviewSupplementType supplementType,
        String content,
        LocalDateTime supplementedAt
) {
}
