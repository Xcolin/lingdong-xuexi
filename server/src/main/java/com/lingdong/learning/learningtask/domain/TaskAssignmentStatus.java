package com.lingdong.learning.learningtask.domain;

/** 学生任务实例的持久化基础状态；暂停状态由活动暂停记录派生。 */
public enum TaskAssignmentStatus {
    PENDING_CLAIM,
    IN_PROGRESS,
    PENDING_REVIEW,
    NEEDS_IMPROVEMENT,
    EXEMPT,
    COMPLETED
}
