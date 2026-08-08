package com.lingdong.learning.learningtask.domain;

/** 可审计的任务实例操作类型。 */
public enum TaskAssignmentEventType {
    CLAIMED,
    PAUSED,
    RESUMED,
    ABANDONED,
    CHECKED_IN,
    REVIEW_REJECTED,
    REVIEW_APPROVED,
    REVIEWER_TRANSFERRED,
    EXEMPTED,
    POINT_CORRECTED,
    MARKED_NEEDS_IMPROVEMENT
}
