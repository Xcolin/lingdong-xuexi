package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.TaskPauseType;

import java.time.LocalDateTime;

/** 学生任务当前有效暂停摘要。 */
public record ActiveTaskPauseView(
        Long id,
        TaskPauseType pauseType,
        LocalDateTime startedAt,
        LocalDateTime expiresAt
) {
}
