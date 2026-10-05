package com.lingdong.learning.organization.domain;

/** 组织节点生命周期不可变审计事件。 */
public enum OrganizationChangeAuditEvent {
    CREATE,
    DIRECT_UPDATE,
    DIRECT_REORDER,
    DIRECT_MOVE,
    ENABLE,
    DISABLE,
    REQUEST,
    APPLY,
    REJECT,
    EXECUTION_FAILED
}
