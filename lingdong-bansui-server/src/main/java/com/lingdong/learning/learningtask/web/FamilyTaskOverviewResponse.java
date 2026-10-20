package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;
import com.lingdong.learning.learningtask.infrastructure.persistence.FamilyTaskOverviewRow;

import java.time.LocalDate;

/** 家长任务列表卡片响应（02-02：图标 / 进度 N/M / 成员 / 频率 / 状态徽标）。 */
public record FamilyTaskOverviewResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
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
    static FamilyTaskOverviewResponse from(FamilyTaskOverviewRow row) {
        return new FamilyTaskOverviewResponse(
                row.id(), row.title(), row.taskType(), row.scheduledDate(), row.endDate(),
                row.visibility(), row.status(), row.recurrenceEnabled(), row.recurrenceFrequency(),
                row.memberNames(), row.assignmentTotal(), row.assignmentCompleted(),
                row.assignmentPendingReview());
    }
}
