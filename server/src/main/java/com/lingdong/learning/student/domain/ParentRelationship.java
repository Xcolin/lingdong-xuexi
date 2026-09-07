package com.lingdong.learning.student.domain;

import java.time.LocalDateTime;

/** 家长与学生当前关系的持久化快照。 */
public record ParentRelationship(
        Long id,
        Long parentUserId,
        Long studentId,
        ParentRelationshipRole role,
        String status,
        String primaryScopeKey,
        LocalDateTime boundAt,
        LocalDateTime unboundAt
) {
}
