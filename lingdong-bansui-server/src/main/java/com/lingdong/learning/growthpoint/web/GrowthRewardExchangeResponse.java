package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchange;
import com.lingdong.learning.growthpoint.domain.GrowthRewardExchangeStatus;

import java.time.LocalDateTime;

/** 奖励兑换响应，全部雪花标识按字符串序列化。 */
public record GrowthRewardExchangeResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        @JsonSerialize(using = ToStringSerializer.class) Long rewardId,
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String rewardName,
        long requiredPoints,
        String description,
        LocalDateTime requestedAt,
        LocalDateTime approvalDeadline,
        GrowthRewardExchangeStatus status,
        @JsonSerialize(using = ToStringSerializer.class) Long reviewedBy,
        LocalDateTime reviewedAt,
        String rejectReason,
        @JsonSerialize(using = ToStringSerializer.class) Long verifiedBy,
        LocalDateTime verifiedAt
) {
    static GrowthRewardExchangeResponse from(GrowthRewardExchange exchange) {
        return new GrowthRewardExchangeResponse(
                exchange.id(), exchange.rewardId(), exchange.studentId(),
                exchange.rewardNameSnapshot(), exchange.requiredPointsSnapshot(),
                exchange.descriptionSnapshot(), exchange.requestedAt(), exchange.approvalDeadline(),
                exchange.status(), exchange.reviewedBy(), exchange.reviewedAt(),
                exchange.rejectReason(), exchange.verifiedBy(), exchange.verifiedAt());
    }
}
