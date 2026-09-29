package com.lingdong.learning.growthpoint.application;

import java.time.LocalDateTime;

/** 学生奖励页所需的最小积分摘要，不暴露账户主键和内部版本号。 */
public record GrowthRewardAccountSummary(
        Long studentId,
        long availablePoints,
        LocalDateTime updatedAt
) {
}
