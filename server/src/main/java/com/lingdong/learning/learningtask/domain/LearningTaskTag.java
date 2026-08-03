package com.lingdong.learning.learningtask.domain;

import java.time.LocalDateTime;

/** 学习任务标签快照。 */
public record LearningTaskTag(Long id, Long taskId, String tagCode, LocalDateTime createdAt) {
}
