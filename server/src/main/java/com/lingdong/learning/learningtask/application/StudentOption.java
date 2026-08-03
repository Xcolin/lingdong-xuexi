package com.lingdong.learning.learningtask.application;

/** 任务表单可选择的脱敏学生信息。 */
public record StudentOption(
        Long id,
        String studentName,
        String studentAccountMasked,
        Long currentClassId,
        String currentClassName
) {
}
