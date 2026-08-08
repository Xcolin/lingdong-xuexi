package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.TaskReviewPage;

import java.util.List;

/** Web 审核待办分页响应。 */
public record TaskReviewPageResponse(
        List<TaskReviewResponse> items,
        int page,
        int pageSize,
        long total
) {
    static TaskReviewPageResponse from(TaskReviewPage page) {
        return new TaskReviewPageResponse(
                page.items().stream().map(TaskReviewResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
