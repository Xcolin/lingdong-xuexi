package com.lingdong.learning.student.web;

import com.lingdong.learning.student.application.ParentRelationshipView;

/** 以字符串传输雪花标识的当前家长关系响应。 */
public record ParentRelationshipResponse(
        String studentId,
        String primaryParentUserId,
        String secondaryParentUserId,
        ParentRelationshipMemberResponse primaryParent,
        ParentRelationshipMemberResponse secondaryParent
) {
    public static ParentRelationshipResponse from(ParentRelationshipView view) {
        return new ParentRelationshipResponse(
                view.studentId().toString(), text(view.primaryParentUserId()),
                text(view.secondaryParentUserId()),
                ParentRelationshipMemberResponse.from(view.primaryParent()),
                ParentRelationshipMemberResponse.from(view.secondaryParent()));
    }

    private static String text(Long value) {
        return value == null ? null : value.toString();
    }
}
