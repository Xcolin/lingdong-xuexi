package com.lingdong.learning.learningtask.domain;

/** 可审计的任务实例操作类型。 */
public enum TaskAssignmentEventType {
    CLAIMED,
    PAUSED,
    RESUMED,
    ABANDONED,
    CHECKED_IN,
    REVIEW_REJECTED,
    REVIEWER_TRANSFERRED,
    EXEMPTED
}
