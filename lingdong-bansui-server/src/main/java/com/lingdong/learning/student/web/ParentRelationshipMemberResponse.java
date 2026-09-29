package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentRelationshipMemberView;
import com.lingdong.learning.student.domain.ParentRelationshipRole;

/** 当前家长关系成员的脱敏响应。 */
public record ParentRelationshipMemberResponse(
        String userId,
        String displayName,
        String mobileMasked,
        ParentRelationshipRole relationshipRole
) {
    static ParentRelationshipMemberResponse from(ParentRelationshipMemberView view) {
        if (view == null) {
            return null;
        }
        return new ParentRelationshipMemberResponse(
                view.userId().toString(), view.displayName(), view.mobileMasked(),
                view.relationshipRole());
    }
}
