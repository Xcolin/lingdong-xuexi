package com.lingdong.learning.learningtask.application;

import java.util.List;

/** 可顺延任务实例分页结果。 */
public record ManagedDeferCandidatePage(
        List<ManagedDeferCandidateView> items,
        int page,
        int pageSize,
        long total
) {
}
