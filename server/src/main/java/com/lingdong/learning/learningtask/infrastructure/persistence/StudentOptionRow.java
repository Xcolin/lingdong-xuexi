package com.lingdong.learning.learningtask.infrastructure.persistence;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 持久化层学生候选行，账号只能交给应用服务脱敏。 */
public record StudentOptionRow(
        Long id,
        String studentName,
        String studentAccount,
        Long currentClassId,
        String currentClassName,
        ParentRelationshipRole relationshipRole
) {
}
