package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.infrastructure.persistence.FamilyTaskOverviewRow;

import java.util.List;

/** 家长任务列表卡片分页（执行态 Tab + 聚合进度）。 */
public record FamilyTaskOverviewPage(
        List<FamilyTaskOverviewRow> items,
        int page,
        int pageSize,
        long total
) {
}
