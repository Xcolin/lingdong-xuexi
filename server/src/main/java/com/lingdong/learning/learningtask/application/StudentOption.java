package com.lingdong.learning.learningtask.application;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 任务表单可选择的脱敏学生信息。 */
public record StudentOption(
        Long id,
        String studentName,
        String studentAccountMasked,
        Long currentClassId,
        String currentClassName,
        ParentRelationshipRole relationshipRole
) {
}
