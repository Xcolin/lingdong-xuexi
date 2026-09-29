package com.lingdong.learning.learningtask.web;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lingdong.learning.learningtask.application.LearningTaskTemplateView;

import java.time.LocalDateTime;
import java.util.List;

public record LearningTaskTemplateResponse(
        @JsonFormat(shape = JsonFormat.Shape.STRING) Long id,
        String templateScope,
        String templateName,
        String taskTitle,
        Integer difficultyLevel,
        Integer durationMinutes,
        String categoryCode,
        List<String> tagCodes,
        String remark,
        Integer sortOrder,
        Long versionNo,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    static LearningTaskTemplateResponse from(LearningTaskTemplateView template) {
        return new LearningTaskTemplateResponse(
                template.id(), template.templateScope(), template.templateName(),
                template.taskTitle(), template.difficultyLevel(), template.durationMinutes(),
                template.categoryCode(), template.tagCodes(), template.remark(),
                template.sortOrder(), template.versionNo(), template.createdAt(),
                template.updatedAt());
    }
}
