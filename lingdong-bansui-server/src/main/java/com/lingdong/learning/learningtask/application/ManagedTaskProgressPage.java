package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 学习任务学生进度分页。 */
public record ManagedTaskProgressPage(
        List<ManagedTaskProgressView> items,
        int page,
        int pageSize,
        long total
) {
}
