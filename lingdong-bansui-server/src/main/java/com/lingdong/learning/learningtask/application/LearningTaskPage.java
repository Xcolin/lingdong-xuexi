package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTask;

import java.util.List;

/** 可管理任务分页结果。 */
public record LearningTaskPage(List<LearningTask> items, int page, int pageSize, long total) {
    public LearningTaskPage {
        items = List.copyOf(items);
    }
}
