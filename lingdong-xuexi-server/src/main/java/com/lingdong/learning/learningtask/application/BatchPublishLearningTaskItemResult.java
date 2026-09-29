package com.lingdong.learning.learningtask.application;

/** 批量发布中的单任务结果。 */
public record BatchPublishLearningTaskItemResult(
        Long taskId,
        boolean success,
        Integer assignmentCount,
        String failureReason
) {
    public static BatchPublishLearningTaskItemResult success(
            Long taskId, int assignmentCount
    ) {
        return new BatchPublishLearningTaskItemResult(taskId, true, assignmentCount, null);
    }

    public static BatchPublishLearningTaskItemResult failure(
            Long taskId, String failureReason
    ) {
        return new BatchPublishLearningTaskItemResult(taskId, false, null, failureReason);
    }
}
