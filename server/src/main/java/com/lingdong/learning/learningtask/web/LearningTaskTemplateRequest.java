package com.lingdong.learning.learningtask.web;

import com.lingdong.learning.learningtask.application.LearningTaskTemplateInput;

import java.util.List;

public record LearningTaskTemplateRequest(
        String templateName,
        String taskTitle,
        Integer difficultyLevel,
        Integer durationMinutes,
        String categoryCode,
        List<String> tagCodes,
        String remark
) {
    LearningTaskTemplateInput toInput() {
        return new LearningTaskTemplateInput(
                templateName, taskTitle, difficultyLevel, durationMinutes,
                categoryCode, tagCodes, remark);
    }
}
