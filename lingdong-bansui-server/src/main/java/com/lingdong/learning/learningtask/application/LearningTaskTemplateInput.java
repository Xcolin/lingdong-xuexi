package com.lingdong.learning.learningtask.application;

import java.util.List;

public record LearningTaskTemplateInput(
        String templateName,
        String taskTitle,
        Integer difficultyLevel,
        Integer durationMinutes,
        String categoryCode,
        List<String> tagCodes,
        String remark
) {
}
