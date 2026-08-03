package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 一次任务暂停记录。 */
public record TaskPause(
        Long id,
        Long assignmentId,
        TaskPauseType pauseType,
        Long startedByUserId,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        LocalDateTime resumedAt,
        String closeType
) {
}
