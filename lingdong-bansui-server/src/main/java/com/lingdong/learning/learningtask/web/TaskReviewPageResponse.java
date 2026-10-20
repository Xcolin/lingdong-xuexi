package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.TaskReviewPage;

import java.util.List;
import java.util.Map;

/** Web 审核待办分页响应（statusCounts 供小程序四状态 Tab 计数）。 */
public record TaskReviewPageResponse(
        List<TaskReviewResponse> items,
        int page,
        int pageSize,
        long total,
        Map<String, Long> statusCounts
) {
    static TaskReviewPageResponse from(TaskReviewPage page) {
        return new TaskReviewPageResponse(
                page.items().stream().map(TaskReviewResponse::from).toList(),
                page.page(), page.pageSize(), page.total(), page.statusCounts());
    }
}
