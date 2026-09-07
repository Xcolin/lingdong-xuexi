package com.lingdong.learning.student.domain;

/** 不可变家长关系变更类型。 */
public enum ParentRelationshipChangeType {
    BIND_SECONDARY,
    UNBIND_SECONDARY,
    UNBIND_PRIMARY_PROMOTE,
    UNBIND_PRIMARY_ORPHAN,
    TRANSFER_PRIMARY,
    REBIND_PRIMARY
}
