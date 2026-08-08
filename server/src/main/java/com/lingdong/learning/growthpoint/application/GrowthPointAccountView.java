package com.lingdong.learning.growthpoint.application;

import java.time.LocalDateTime;

/** 面向学生本人或主家长的积分账户快照。 */
public record GrowthPointAccountView(
        Long studentId,
        String studentName,
        long totalPoints,
        long availablePoints,
        LocalDateTime updatedAt
) {
}
