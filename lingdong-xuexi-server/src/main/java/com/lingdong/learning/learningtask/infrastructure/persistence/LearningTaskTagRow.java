package com.lingdong.learning.learningtask.infrastructure.persistence;

/** 用于批量聚合任务标签的数据库行。 */
public record LearningTaskTagRow(Long taskId, String tagCode) {
}
