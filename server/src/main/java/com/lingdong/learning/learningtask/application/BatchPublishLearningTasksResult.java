package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 批量发布汇总结果，不把部分失败表示为整体成功。 */
public record BatchPublishLearningTasksResult(
        int successCount,
        int failureCount,
        List<BatchPublishLearningTaskItemResult> items
) {
    public BatchPublishLearningTasksResult {
        items = List.copyOf(items);
    }
}
