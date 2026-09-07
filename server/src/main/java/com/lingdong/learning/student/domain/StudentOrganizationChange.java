package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 学生机构关系的不可变变更记录。 */
public record StudentOrganizationChange(
        Long id,
        Long studentId,
        StudentOrganizationChangeType changeType,
        Long fromOrganizationId,
        Long toOrganizationId,
        String reason,
        Long operatorUserId,
        LocalDateTime occurredAt,
        LocalDateTime createdAt
) {
}
