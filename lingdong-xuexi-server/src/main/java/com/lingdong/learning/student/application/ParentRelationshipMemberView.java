package com.lingdong.learning.student.application;

import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 当前家长关系成员的安全展示信息。 */
public record ParentRelationshipMemberView(
        Long userId,
        String displayName,
        String mobileMasked,
        ParentRelationshipRole relationshipRole
) {
}
