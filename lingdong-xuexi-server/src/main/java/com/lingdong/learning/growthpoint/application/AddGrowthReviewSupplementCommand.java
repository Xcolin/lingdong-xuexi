package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthReviewSupplementType;

/** 追加成长复盘补录命令。 */
public record AddGrowthReviewSupplementCommand(
        GrowthReviewSupplementType supplementType,
        String content
) {
}
