package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 发布前保留的原始组织或学生目标。 */
public record LearningTaskTarget(
        Long id,
        Long taskId,
        LearningTaskTargetType targetType,
        Long targetId,
        LocalDateTime createdAt
) {
}
