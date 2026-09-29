package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskStatus;

/** 单任务发布结果。 */
public record PublishLearningTaskResult(Long taskId, int assignmentCount, LearningTaskStatus status) {
}
