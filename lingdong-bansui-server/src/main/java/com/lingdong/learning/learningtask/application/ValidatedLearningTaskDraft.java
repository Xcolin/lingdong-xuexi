package com.lingdong.learning.learningtask.application;

import java.time.LocalDate;
import java.util.List;

/** 已完成字段和字典校验的规范化任务草稿。 */
public record ValidatedLearningTaskDraft(
        String title,
        int difficultyLevel,
        int basePoints,
        int durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        List<LearningTaskTargetInput> targets,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate,
        String recurrenceFrequency, java.util.List<Integer> recurrenceWeekdays,
        String description, List<String> requirementItems, List<Long> exampleFileIds,
        String taskType, LocalDate endDate, String visibility
) {
    public ValidatedLearningTaskDraft {
        tagCodes = List.copyOf(tagCodes);
        targets = List.copyOf(targets);
        requirementItems = requirementItems == null ? List.of() : List.copyOf(requirementItems);
        exampleFileIds = exampleFileIds == null ? List.of() : List.copyOf(exampleFileIds);
    }

    public ValidatedLearningTaskDraft(
        String title,
        int difficultyLevel,
        int basePoints,
        int durationMinutes,
        LocalDate scheduledDate,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        List<LearningTaskTargetInput> targets,
        boolean recurrenceEnabled,
        LocalDate recurrenceEndDate
    ) { this(title, difficultyLevel, basePoints, durationMinutes, scheduledDate, categoryCode, tagCodes, remark, targets, recurrenceEnabled, recurrenceEndDate, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null); }

    public ValidatedLearningTaskDraft(
            String title,
            int difficultyLevel,
            int basePoints,
            int durationMinutes,
            LocalDate scheduledDate,
            String categoryCode,
            List<String> tagCodes,
            String remark,
            List<LearningTaskTargetInput> targets
    ) {
        this(title, difficultyLevel, basePoints, durationMinutes, scheduledDate,
                categoryCode, tagCodes, remark, targets, false, null, "DAILY", java.util.List.of(), null, java.util.List.of(), java.util.List.of(), null, null, null);
    }
}
