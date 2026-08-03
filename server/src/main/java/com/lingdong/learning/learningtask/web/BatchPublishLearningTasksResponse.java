package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.BatchPublishLearningTasksResult;

import java.util.List;

/** 批量发布汇总响应。 */
public record BatchPublishLearningTasksResponse(
        int successCount,
        int failureCount,
        List<BatchPublishLearningTaskItemResponse> items
) {
    static BatchPublishLearningTasksResponse from(BatchPublishLearningTasksResult result) {
        return new BatchPublishLearningTasksResponse(
                result.successCount(), result.failureCount(), result.items().stream()
                .map(BatchPublishLearningTaskItemResponse::from).toList());
    }
}
