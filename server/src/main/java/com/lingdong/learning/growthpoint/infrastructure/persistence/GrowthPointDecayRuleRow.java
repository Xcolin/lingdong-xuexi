package com.lingdong.learning.growthpoint.infrastructure.persistence;

import java.time.LocalDateTime;

/** 当前奖励时点命中的可追溯积分衰减规则。 */
public record GrowthPointDecayRuleRow(
        Long id,
        Integer startStreakDay,
        Integer decayPercent,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        Integer versionNo
) {
}
