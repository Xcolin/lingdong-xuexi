package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.application.GrowthPointAccountView;

import java.time.LocalDateTime;

/** 积分账户响应，不暴露内部乐观锁版本。 */
public record GrowthPointAccountResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        String studentName,
        long totalPoints,
        long availablePoints,
        LocalDateTime updatedAt,
        long weekEarnedPoints,
        long monthEarnedPoints
) {
    public GrowthPointAccountResponse(Long studentId, String studentName, long totalPoints, long availablePoints, LocalDateTime updatedAt) {
        this(studentId, studentName, totalPoints, availablePoints, updatedAt, 0, 0);
    }
    static GrowthPointAccountResponse from(GrowthPointAccountView view) {
        return new GrowthPointAccountResponse(
                view.studentId(), view.studentName(), view.totalPoints(),
                view.availablePoints(), view.updatedAt(), view.weekEarnedPoints(), view.monthEarnedPoints());
    }
}
