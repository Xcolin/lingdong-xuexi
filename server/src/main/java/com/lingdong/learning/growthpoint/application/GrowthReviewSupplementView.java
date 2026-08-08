package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;

import java.time.LocalDateTime;

/** 成长复盘补录内容。 */
public record GrowthReviewSupplementView(
        Long id,
        Long editorUserId,
        String editorRole,
        GrowthReviewSupplementType supplementType,
        String content,
        LocalDateTime supplementedAt
) {
}
