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
        LocalDate recurrenceEndDate
) {
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
                tagCodes, remark, targets, false, null);
    }
}
