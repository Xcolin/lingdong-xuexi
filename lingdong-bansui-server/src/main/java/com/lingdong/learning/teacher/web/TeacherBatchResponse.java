package com.lingdong.learning.teacher.web;

import com.lingdong.learning.teacher.application.TeacherBatchResult;

import java.util.List;

/** 教师批量操作汇总响应。 */
public record TeacherBatchResponse(
        int successCount,
        int failureCount,
        List<TeacherBatchItemResponse> items
) {
    static TeacherBatchResponse from(TeacherBatchResult result) {
        return new TeacherBatchResponse(
                result.successCount(), result.failureCount(),
                result.items().stream().map(TeacherBatchItemResponse::from).toList());
    }
}
