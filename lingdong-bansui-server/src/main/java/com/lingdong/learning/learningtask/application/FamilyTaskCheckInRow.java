package com.lingdong.learning.learningtask.application;

import java.time.LocalDateTime;

/** 家长任务详情打卡记录行（02-06 打卡记录流）。 */
public record FamilyTaskCheckInRow(
        Long checkInId,
        Long assignmentId,
        Long studentId,
        String studentName,
        String content,
        String status,
        LocalDateTime submittedAt,
        String completion
) {
}
