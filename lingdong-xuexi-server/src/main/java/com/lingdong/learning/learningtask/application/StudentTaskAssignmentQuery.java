package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.learningtask.domain.LearningTaskSourceType;

import java.time.LocalDate;

/** 学生本人任务实例查询参数。 */
public record StudentTaskAssignmentQuery(
        Long studentId,
        LearningTaskSourceType sourceType,
        LocalDate scheduledDate,
        int offset,
        int limit
) {
}
