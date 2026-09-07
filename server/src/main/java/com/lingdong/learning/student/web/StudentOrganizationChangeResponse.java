package com.lingdong.learning.student.web;

import com.lingdong.learning.student.domain.StudentOrganizationChange;

import java.time.LocalDateTime;

/** 学生机构关系变更的安全响应。 */
public record StudentOrganizationChangeResponse(
        String id,
        String changeType,
        String fromOrganizationId,
        String toOrganizationId,
        String reason,
        String operatorUserId,
        LocalDateTime occurredAt
) {
    static StudentOrganizationChangeResponse from(StudentOrganizationChange change) {
        return new StudentOrganizationChangeResponse(
                change.id().toString(),
                change.changeType().name(),
                stringValue(change.fromOrganizationId()),
                stringValue(change.toOrganizationId()),
                change.reason(),
                change.operatorUserId().toString(),
                change.occurredAt());
    }

    private static String stringValue(Long value) {
        return value == null ? null : value.toString();
    }
}
