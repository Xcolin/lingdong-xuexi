package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.LearningTaskPage;

import java.util.List;

/** 可管理任务分页响应。 */
public record LearningTaskPageResponse(
        List<LearningTaskSummaryResponse> items, int page, int pageSize, long total
) {
    static LearningTaskPageResponse from(LearningTaskPage page) {
        return new LearningTaskPageResponse(
                page.items().stream().map(LearningTaskSummaryResponse::from).toList(),
                page.page(), page.pageSize(), page.total());
    }
}
