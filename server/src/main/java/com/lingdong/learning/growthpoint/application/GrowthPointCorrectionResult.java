package com.lingdong.learning.growthpoint.application;

import com.lingdong.learning.learningtask.domain.TaskAssignmentStatus;

import java.time.LocalDateTime;

/** 积分纠错完成后的账户和任务状态摘要。 */
public record GrowthPointCorrectionResult(
        Long studentId,
        Long assignmentId,
        Long originalLedgerId,
        Long correctionLedgerId,
        long correctedPoints,
        long totalPoints,
        long availablePoints,
        TaskAssignmentStatus currentStatus,
        LocalDateTime occurredAt
) {
}
