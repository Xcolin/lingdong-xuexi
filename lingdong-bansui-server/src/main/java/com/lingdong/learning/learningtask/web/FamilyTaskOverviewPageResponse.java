package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.FamilyTaskOverviewPage;

import java.util.List;

/** 家长任务列表卡片分页响应。 */
public record FamilyTaskOverviewPageResponse(
        List<FamilyTaskOverviewResponse> items,
        int page,
        int pageSize,
        long total
) {
    public static FamilyTaskOverviewPageResponse from(FamilyTaskOverviewPage result) {
        return new FamilyTaskOverviewPageResponse(
                result.items().stream().map(FamilyTaskOverviewResponse::from).toList(),
                result.page(), result.pageSize(), result.total());
    }
}
