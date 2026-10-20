-- 学生任务顺延申请（78 视图审计 05 组 3.8）：事件类型放开 DEFERRED（学生顺延原因经审计事件持久化）。
ALTER TABLE learn_task_assignment_event
DROP CONSTRAINT ck_task_assignment_event_type;

ALTER TABLE learn_task_assignment_event
ADD CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEW_APPROVED', 'REVIEWER_TRANSFERRED', 'EXEMPTED',
            'POINT_CORRECTED', 'MARKED_NEEDS_IMPROVEMENT', 'CLASS_INVALIDATED', 'DEFERRED')
    );
