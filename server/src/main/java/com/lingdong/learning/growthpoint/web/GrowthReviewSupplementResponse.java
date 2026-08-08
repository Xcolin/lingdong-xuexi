package com.lingdong.learning.growthpoint.web;

import com.lingdong.learning.growthpoint.application.GrowthReviewSupplementView;
import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;

import java.time.LocalDateTime;

/** 成长复盘补录响应；雪花标识按字符串输出。 */
public record GrowthReviewSupplementResponse(
        String id,
        String editorUserId,
        String editorRole,
        GrowthReviewSupplementType supplementType,
        String content,
        LocalDateTime supplementedAt
) {
    public static GrowthReviewSupplementResponse from(GrowthReviewSupplementView view) {
        return new GrowthReviewSupplementResponse(
                view.id().toString(), view.editorUserId().toString(), view.editorRole(),
                view.supplementType(), view.content(), view.supplementedAt());
    }
}
