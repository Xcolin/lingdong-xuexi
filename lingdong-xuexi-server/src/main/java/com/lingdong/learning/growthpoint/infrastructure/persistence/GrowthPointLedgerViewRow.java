package com.lingdong.learning.growthpoint.infrastructure.persistence;

import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

import java.time.LocalDateTime;

/** 积分台账及来源任务信息的只读查询行。 */
public record GrowthPointLedgerViewRow(
        Long id,
        GrowthPointChangeType changeType,
        Long amount,
        Long availableDelta,
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
        TaskAssignmentStatus assignmentStatus,
        Long assignmentReviewerUserId
) {
}
