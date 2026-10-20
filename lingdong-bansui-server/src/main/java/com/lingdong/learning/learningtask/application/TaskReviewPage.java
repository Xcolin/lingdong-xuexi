package com.lingdong.learning.learningtask.application;

import java.util.List;
import java.util.Map;

/** 审核待办分页结果（statusCounts 为各审核状态计数）。 */
public record TaskReviewPage(
        List<TaskReviewView> items, int page, int pageSize, long total,
        Map<String, Long> statusCounts) {
    public TaskReviewPage {
        items = List.copyOf(items);
        statusCounts = statusCounts == null ? Map.of() : Map.copyOf(statusCounts);
    }
}
