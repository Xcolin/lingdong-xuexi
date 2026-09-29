package com.lingdong.learning.growthpoint.infrastructure.persistence;

import java.time.LocalDateTime;

/** 积分账户与学生档案联表后的只读查询行。 */
public record GrowthPointAccountViewRow(
        Long studentId,
        String studentName,
        Long totalPoints,
        Long availablePoints,
        LocalDateTime updatedAt
) {
}
