package com.lingdong.learning.learningtask.application;

import java.time.LocalDateTime;

/** 学生任务最近一次打卡摘要。 */
public record TaskCheckInView(
        Long id,
        Integer submissionNo,
        String content,
        String status,
        LocalDateTime submittedAt,
        String reviewComment
) {
}
