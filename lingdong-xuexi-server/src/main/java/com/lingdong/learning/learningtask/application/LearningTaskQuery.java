package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;
import com.lingdong.learning.learningtask.domain.LearningTaskStatus;

import java.time.LocalDate;

/** 可管理任务分页查询。 */
public record LearningTaskQuery(
        Long currentUserId,
        boolean parent,
        boolean organizationAdministrator,
        boolean teacher,
        LearningTaskSourceType sourceType,
        LearningTaskStatus status,
        LocalDate scheduledDate,
        String keyword,
        int offset,
        int limit
) {
}
