package com.lingdong.learning.learningtask.application;

/** 单个每日固定任务计划的补生成结果。 */
public record RecurringTaskGenerationResult(
        Long recurrenceId,
        int generatedDateCount,
        int generatedAssignmentCount,
        boolean completed
) {
}
