package com.lingdong.learning.growthpoint.application;

/** 单个学生本次沉睡处理产生的可审计结果。 */
public record GrowthPointDormancyResult(
        boolean reminderCreated,
        boolean cleared,
        long clearedPoints,
        boolean activityReset
) {
}
