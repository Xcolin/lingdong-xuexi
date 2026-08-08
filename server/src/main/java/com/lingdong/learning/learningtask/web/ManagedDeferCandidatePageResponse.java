package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.ManagedDeferCandidatePage;

import java.util.List;

/** 管理端可顺延任务分页响应。 */
public record ManagedDeferCandidatePageResponse(
        List<ManagedDeferCandidateResponse> items,
        int page,
        int pageSize,
        long total
) {
    static ManagedDeferCandidatePageResponse from(ManagedDeferCandidatePage result) {
        return new ManagedDeferCandidatePageResponse(
                result.items().stream().map(ManagedDeferCandidateResponse::from).toList(),
                result.page(), result.pageSize(), result.total());
    }
}
