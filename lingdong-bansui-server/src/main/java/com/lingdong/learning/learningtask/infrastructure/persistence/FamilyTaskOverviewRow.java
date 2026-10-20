package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.learningtask.domain.LearningTaskStatus;

import java.time.LocalDate;

/** 家长任务列表卡片聚合行：任务定义 + 执行进度统计 + 成员名（02-02 任务卡）。 */
public record FamilyTaskOverviewRow(
        Long id,
        String title,
        String taskType,
        LocalDate scheduledDate,
        LocalDate endDate,
        String visibility,
        LearningTaskStatus status,
        boolean recurrenceEnabled,
        String recurrenceFrequency,
        String memberNames,
        long assignmentTotal,
        long assignmentCompleted,
        long assignmentPendingReview
) {
}
