package com.lingdong.learning.growthpoint.infrastructure.persistence;

import java.time.LocalDateTime;

/** 锁定后用于沉睡提醒、清零和活跃重置的学生生命周期状态。 */
public record GrowthPointDormancyStateRow(
        Long id,
        Long studentId,
        LocalDateTime lastActivityAt,
        LocalDateTime reminderDueAt,
        LocalDateTime clearDueAt,
        LocalDateTime lastReminderCreatedAt,
        LocalDateTime lastClearedAt,
        Integer versionNo
) {
}
