package com.lingdong.learning.growthpoint.application;

/** 任务奖励在发放前固化的基础积分、实发积分和衰减审计结果。 */
public record GrowthPointAwardCalculation(
        int basePoints,
        int awardedPoints,
        int streakDays,
        int decayPercent,
        Long decayRuleId
) {
    public GrowthPointAwardCalculation {
        if (basePoints <= 0 || awardedPoints <= 0 || awardedPoints > basePoints) {
            throw new IllegalArgumentException("积分计算结果不合法");
        }
        if (streakDays < 1 || decayPercent < 0 || decayPercent > 40) {
            throw new IllegalArgumentException("积分衰减审计值不合法");
        }
        if ((decayPercent == 0) != (decayRuleId == null)) {
            throw new IllegalArgumentException("积分衰减规则与比例不一致");
        }
    }
}
