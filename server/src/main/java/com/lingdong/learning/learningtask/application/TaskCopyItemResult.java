package com.lingdong.learning.learningtask.application;

/** 单个昨日任务复制结果。 */
public record TaskCopyItemResult(
        Long itemId,
        Long sourceTaskId,
        Long targetTaskId,
        String taskTitle,
        String status,
        String failureCode,
        String failureMessage,
        int retryCount
) {
}
