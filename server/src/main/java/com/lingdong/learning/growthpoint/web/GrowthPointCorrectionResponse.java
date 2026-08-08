package com.lingdong.learning.growthpoint.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.growthpoint.application.GrowthPointCorrectionResult;
import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

import java.time.LocalDateTime;

/** 积分纠错成功响应，长整型标识按字符串输出。 */
public record GrowthPointCorrectionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long studentId,
        @JsonSerialize(using = ToStringSerializer.class) Long assignmentId,
        @JsonSerialize(using = ToStringSerializer.class) Long originalLedgerId,
        @JsonSerialize(using = ToStringSerializer.class) Long correctionLedgerId,
        long correctedPoints,
        long totalPoints,
        long availablePoints,
        TaskAssignmentStatus currentStatus,
        LocalDateTime occurredAt
) {
    static GrowthPointCorrectionResponse from(GrowthPointCorrectionResult result) {
        return new GrowthPointCorrectionResponse(
                result.studentId(), result.assignmentId(), result.originalLedgerId(),
                result.correctionLedgerId(), result.correctedPoints(), result.totalPoints(),
                result.availablePoints(), result.currentStatus(), result.occurredAt());
    }
}
