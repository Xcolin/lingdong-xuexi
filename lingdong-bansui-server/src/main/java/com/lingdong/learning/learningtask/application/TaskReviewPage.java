package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 审核待办分页结果。 */
public record TaskReviewPage(List<TaskReviewView> items, int page, int pageSize, long total) {
    public TaskReviewPage {
        items = List.copyOf(items);
    }
}
