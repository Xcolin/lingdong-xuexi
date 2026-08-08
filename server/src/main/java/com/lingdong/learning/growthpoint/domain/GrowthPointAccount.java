package com.lingdong.learning.growthpoint.domain;

/** 学生唯一积分账户的锁定快照。 */
public record GrowthPointAccount(
        Long id,
        Long studentId,
        Long totalPoints,
        Long availablePoints,
        Integer versionNo
) {
}
