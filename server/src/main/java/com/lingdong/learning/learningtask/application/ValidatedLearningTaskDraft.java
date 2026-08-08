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
        LocalDate recurrenceEndDate
) {
    public ValidatedLearningTaskDraft {
        tagCodes = List.copyOf(tagCodes);
        targets = List.copyOf(targets);
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
            List<LearningTaskTargetInput> targets
    ) {
        this(title, difficultyLevel, basePoints, durationMinutes, scheduledDate,
                categoryCode, tagCodes, remark, targets, false, null);
    }
}
