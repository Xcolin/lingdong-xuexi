-- V26：积分纠错、任务回退与主家长最小访问权限。

-- 同一任务纠错后允许重新审核发奖；并发幂等由任务状态版本控制。
ALTER TABLE growth_point_ledger
    DROP INDEX uk_growth_point_ledger_task_reward;

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT uk_growth_point_ledger_correction_of UNIQUE (correction_of_id);

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_correction CHECK (
        change_type <> 'CORRECTION'
        OR (correction_of_id IS NOT NULL
            AND source_assignment_id IS NOT NULL
            AND source_type = 'FAMILY'
            AND reviewer_user_id IS NOT NULL
            AND amount < 0
            AND available_delta <= 0
            AND available_delta >= amount)
    );

ALTER TABLE learn_task_assignment_event
    DROP CONSTRAINT ck_task_assignment_event_type;

ALTER TABLE learn_task_assignment_event
    ADD CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEW_APPROVED', 'REVIEWER_TRANSFERRED', 'EXEMPTED',
            'POINT_CORRECTED')
    );

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646380, 'GROWTH_POINT_CORRECTION', '积分纠错',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制主家长在时限内冲销误审积分并将任务回退待审核的能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646381, 'GROWTH_POINT_CORRECT_CHILD', '纠正孩子误审积分',
    'OPERATION', 'WEB', NULL, 180, 'ENABLED',
    '主家长在 Web 端纠正本人误审通过的家庭任务积分。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646382, 1874244142494646277, 1874244142494646381);
