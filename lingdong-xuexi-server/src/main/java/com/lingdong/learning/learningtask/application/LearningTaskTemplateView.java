package com.lingdong.learning.learningtask.application;

import java.time.LocalDateTime;
import java.util.List;

public record LearningTaskTemplateView(
        Long id,
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
}
