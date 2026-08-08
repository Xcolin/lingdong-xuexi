package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.application.GrowthPointLedgerView;
import com.lingdong.learning.growthpoint.domain.GrowthPointChangeType;
import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDateTime;

/** 带任务、机构和审核人来源的积分台账响应。 */
public record GrowthPointLedgerResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        GrowthPointChangeType changeType,
        long amount,
        long availableDelta,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceAssignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceExchangeId,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceTaskId,
        Integer basePointsSnapshot,
        Integer decayPercent,
        Integer streakDays,
        @JsonSerialize(using = ToStringSerializer.class) Long decayRuleId,
        LearningTaskSourceType sourceType,
        @JsonSerialize(using = ToStringSerializer.class) Long sourceOrganizationId,
        String sourceOrganizationName,
        String taskTitle,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewerUserId,
        String reviewerDisplayName,
        LocalDateTime occurredAt,
        String remark,
        @JsonSerialize(using = ToStringSerializer.class) Long correctionOfId,
        @JsonSerialize(using = ToStringSerializer.class) Long correctionLedgerId,
        LocalDateTime correctionDeadline,
        boolean correctable
) {
    static GrowthPointLedgerResponse from(GrowthPointLedgerView view) {
        return new GrowthPointLedgerResponse(
                view.id(), view.changeType(), view.amount(), view.availableDelta(),
                view.sourceAssignmentId(), view.sourceExchangeId(), view.sourceTaskId(),
                view.basePointsSnapshot(), view.decayPercent(), view.streakDays(), view.decayRuleId(),
                view.sourceType(), view.sourceOrganizationId(),
                view.sourceOrganizationName(), view.taskTitle(), view.reviewerUserId(),
                view.reviewerDisplayName(), view.occurredAt(), view.remark(),
                view.correctionOfId(), view.correctionLedgerId(), view.correctionDeadline(),
                view.correctable());
    }
}
