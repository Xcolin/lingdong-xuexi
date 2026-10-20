package com.lingdong.learning.learningtask.application;

import java.time.LocalDate;
import java.util.List;

/** 与来源授权无关的任务草稿字段输入。 */
public record LearningTaskDraftInput(
        String title,
        Integer difficultyLevel,
        Integer durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        List<LearningTaskTargetInput> targets,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        String recurrenceFrequency, java.util.List<Integer> recurrenceWeekdays,
        String description, java.util.List<String> requirementItems, java.util.List<Long> exampleFileIds,
        String taskType, LocalDate endDate, String visibility
) {
    public LearningTaskDraftInput(
        String title,
        Integer difficultyLevel,
        Integer durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        List<LearningTaskTargetInput> targets,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate
) { this(title, difficultyLevel, durationMinutes, scheduledDate, categoryCode, tagCodes, remark, targets, recurrenceEnabled, recurrenceEndDate, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null); }

    public LearningTaskDraftInput(
        String title,
        Integer difficultyLevel,
        Integer durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        List<LearningTaskTargetInput> targets,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        String recurrenceFrequency, java.util.List<Integer> recurrenceWeekdays
    ) { this(title, difficultyLevel, durationMinutes, scheduledDate, categoryCode, tagCodes, remark, targets, recurrenceEnabled, recurrenceEndDate, recurrenceFrequency, recurrenceWeekdays, null, java.util.List.of(), java.util.List.of(), null, null, null); }

    public LearningTaskDraftInput(
            String title,
            Integer difficultyLevel,
            Integer durationMinutes,
            LocalDate scheduledDate,
            String categoryCode,
            List<String> tagCodes,
            String remark,
            List<LearningTaskTargetInput> targets
    ) {
        this(title, difficultyLevel, durationMinutes, scheduledDate, categoryCode,
                tagCodes, remark, targets, false, null, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null);
    }
}
