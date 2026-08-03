package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 学生的一次不可覆盖打卡提交。 */
public record TaskCheckIn(
        Long id,
        Long assignmentId,
        Integer submissionNo,
        String content,
        String status,
        Long submittedByUserId,
        LocalDateTime submittedAt,
        Long reviewedByUserId,
        LocalDateTime reviewedAt,
        String reviewComment
) {
}
