package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDateTime;

/** 一笔不可变积分台账及其可追溯来源。 */
public record GrowthPointLedgerView(
        Long id,
        GrowthPointChangeType changeType,
        long amount,
        long availableDelta,
        Long sourceAssignmentId,
        Long sourceExchangeId,
        Long sourceTaskId,
        Integer basePointsSnapshot,
        Integer decayPercent,
        Integer streakDays,
        Long decayRuleId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        String sourceOrganizationName,
        String taskTitle,
        Long reviewerUserId,
        String reviewerDisplayName,
        LocalDateTime occurredAt,
        String remark,
        Long correctionOfId,
        Long correctionLedgerId,
        LocalDateTime correctionDeadline,
        boolean correctable
) {
}
