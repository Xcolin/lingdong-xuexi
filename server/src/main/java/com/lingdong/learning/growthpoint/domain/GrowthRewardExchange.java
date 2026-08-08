package com.lingdong.learning.growthpoint.domain;

import java.time.LocalDateTime;

/** 保存奖励快照和处理事实的兑换记录。 */
public record GrowthRewardExchange(
        Long id,
        Long rewardId,
        Long studentId,
        Long requesterUserId,
        String rewardNameSnapshot,
        Long requiredPointsSnapshot,
        String descriptionSnapshot,
        LocalDateTime requestedAt,
        LocalDateTime approvalDeadline,
        GrowthRewardExchangeStatus status,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String rejectReason,
        Long verifiedBy,
        LocalDateTime verifiedAt,
        Integer versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
