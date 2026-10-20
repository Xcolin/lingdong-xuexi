package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.application.GrowthRewardAccountSummary;

import java.time.LocalDateTime;

/** 小程序奖励页积分摘要，雪花学生标识按字符串输出。 */
public record GrowthRewardAccountSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        String currentClassName,
        long availablePoints,
        LocalDateTime updatedAt
) {
    static GrowthRewardAccountSummaryResponse from(GrowthRewardAccountSummary summary) {
        return new GrowthRewardAccountSummaryResponse(
                summary.studentId(), summary.studentName(), summary.currentClassName(),
                summary.availablePoints(), summary.updatedAt());
    }
}
