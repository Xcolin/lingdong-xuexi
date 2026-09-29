package com.lingdong.learning.student.application;

/** 学生当前主副家长关系的最小业务视图。 */
public record ParentRelationshipView(
        Long studentId,
        Long primaryParentUserId,
        Long secondaryParentUserId,
        ParentRelationshipMemberView primaryParent,
        ParentRelationshipMemberView secondaryParent
) {
    public ParentRelationshipView(
            Long studentId,
            Long primaryParentUserId,
            Long secondaryParentUserId
    ) {
        this(studentId, primaryParentUserId, secondaryParentUserId, null, null);
    }
}
