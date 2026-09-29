package com.lingdong.learning.teacher.application;

import java.util.List;

/** 教师批量操作汇总结果。 */
public record TeacherBatchResult(
        int successCount,
        int failureCount,
        List<TeacherBatchItemResult> items
) {
    public TeacherBatchResult {
        items = List.copyOf(items);
    }
}
