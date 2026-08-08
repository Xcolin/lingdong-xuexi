package com.lingdong.learning.growthpoint.domain;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDateTime;

/** 不可覆盖的积分事实记录。 */
public record GrowthPointLedger(
        Long id,
        Long accountId,
        Long studentId,
        Long sourceAssignmentId,
        Long sourceExchangeId,
        LearningTaskSourceType sourceType,
        Long sourceOrganizationId,
        GrowthPointChangeType changeType,
        Long amount,
        Long availableDelta,
        Long reviewerUserId,
        LocalDateTime occurredAt,
        Long correctionOfId,
        String remark,
        Long sourceTaskId,
        Long sourceDormancyNoticeId,
        Integer basePointsSnapshot,
        Integer decayPercent,
        Integer streakDays,
        Long decayRuleId
) {
    public static GrowthPointLedger taskReward(
            Long id,
            Long accountId,
            Long studentId,
            Long sourceAssignmentId,
            Long sourceTaskId,
            LearningTaskSourceType sourceType,
            Long sourceOrganizationId,
            int basePoints,
            long points,
            int streakDays,
            int decayPercent,
            Long decayRuleId,
            Long reviewerUserId,
            LocalDateTime occurredAt
    ) {
        return new GrowthPointLedger(
                id, accountId, studentId, sourceAssignmentId, null, sourceType,
                sourceOrganizationId, GrowthPointChangeType.TASK_REWARD,
                points, points, reviewerUserId, occurredAt, null, "学习任务审核通过奖励",
                sourceTaskId, null, basePoints, decayPercent, streakDays, decayRuleId);
    }

    /** 生成一笔关联原奖励且不可覆盖的反向纠错台账。 */
    public static GrowthPointLedger correction(
            Long id,
            GrowthPointLedger original,
            long availableDeduction,
            Long reviewerUserId,
            LocalDateTime occurredAt,
            String reason
    ) {
        long correctedPoints = Math.negateExact(original.amount());
        return new GrowthPointLedger(
                id, original.accountId(), original.studentId(), original.sourceAssignmentId(),
                null,
                original.sourceType(), original.sourceOrganizationId(), GrowthPointChangeType.CORRECTION,
                correctedPoints, Math.negateExact(availableDeduction), reviewerUserId,
                occurredAt, original.id(), reason, original.sourceTaskId(), null,
                null, null, null, null);
    }

    /** 生成一笔只减少可用积分并关联兑换记录的不可变台账。 */
    public static GrowthPointLedger redemption(
            Long id,
            Long accountId,
            Long studentId,
            Long sourceExchangeId,
            long points,
            Long reviewerUserId,
            LocalDateTime occurredAt,
            String rewardName
    ) {
        return new GrowthPointLedger(
                id, accountId, studentId, null, sourceExchangeId,
                LearningTaskSourceType.FAMILY, null, GrowthPointChangeType.REDEMPTION,
                0L, Math.negateExact(points), reviewerUserId, occurredAt, null,
                "奖励兑换：" + rewardName, null, null, null, null, null, null);
    }

    /** 生成只清空可用积分、不减少累计积分的沉睡处理台账。 */
    public static GrowthPointLedger dormancyClear(
            Long id,
            Long accountId,
            Long studentId,
            Long sourceDormancyNoticeId,
            long clearedPoints,
            LocalDateTime occurredAt
    ) {
        return new GrowthPointLedger(
                id, accountId, studentId, null, null, null, null,
                GrowthPointChangeType.DORMANCY_CLEAR, 0L, Math.negateExact(clearedPoints),
                null, occurredAt, null, "连续30天无有效活跃，清空可用积分",
                null, sourceDormancyNoticeId, null, null, null, null);
    }
}
