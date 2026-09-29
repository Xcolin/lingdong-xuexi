-- V32：增加任务待优化、手动/自动顺延、隔夜迁移标识和不可变顺延历史。
ALTER TABLE learn_task
    ADD COLUMN generation_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL';

ALTER TABLE learn_task
    ADD COLUMN origin_task_id BIGINT NULL;

ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_generation_type CHECK (
        generation_type IN ('NORMAL', 'DEFERRED')
    );

ALTER TABLE learn_task
    ADD CONSTRAINT fk_learn_task_origin_task
        FOREIGN KEY (origin_task_id) REFERENCES learn_task (id);

CREATE INDEX idx_learn_task_origin_generation
    ON learn_task (origin_task_id, generation_type);

ALTER TABLE learn_task_assignment
    ADD COLUMN last_defer_type VARCHAR(16) NULL;

ALTER TABLE learn_task_assignment
    ADD COLUMN defer_count INT NOT NULL DEFAULT 0;

ALTER TABLE learn_task_assignment
    ADD COLUMN overnight_migrated TINYINT NOT NULL DEFAULT 0;

ALTER TABLE learn_task_assignment
    ADD COLUMN last_deferred_by_user_id BIGINT NULL;

ALTER TABLE learn_task_assignment
    ADD COLUMN last_deferred_at TIMESTAMP NULL;

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT ck_learn_task_assignment_defer_type CHECK (
        last_defer_type IS NULL OR last_defer_type IN ('AUTO', 'MANUAL')
    );

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT ck_learn_task_assignment_defer_count CHECK (defer_count >= 0);

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT ck_learn_task_assignment_overnight CHECK (overnight_migrated IN (0, 1));

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT fk_learn_task_assignment_deferred_by
        FOREIGN KEY (last_deferred_by_user_id) REFERENCES sys_user (id);

CREATE INDEX idx_learn_task_assignment_overdue
    ON learn_task_assignment (current_status, due_at, id);

CREATE INDEX idx_learn_task_assignment_auto_defer
    ON learn_task_assignment (scheduled_date, current_status, last_defer_type, id);

CREATE TABLE learn_task_defer_history (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    source_task_id BIGINT NOT NULL,
    target_task_id BIGINT NOT NULL,
    source_scheduled_date DATE NOT NULL,
    target_scheduled_date DATE NOT NULL,
    defer_type VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NULL,
    occurred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_defer_history_assignment
        FOREIGN KEY (assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_task_defer_history_source_task
        FOREIGN KEY (source_task_id) REFERENCES learn_task (id),
    CONSTRAINT fk_task_defer_history_target_task
        FOREIGN KEY (target_task_id) REFERENCES learn_task (id),
    CONSTRAINT fk_task_defer_history_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_defer_history_type CHECK (defer_type IN ('AUTO', 'MANUAL')),
    CONSTRAINT ck_task_defer_history_dates CHECK (target_scheduled_date > source_scheduled_date)
);

CREATE UNIQUE INDEX uk_task_defer_history_assignment_count
    ON learn_task_defer_history (assignment_id, id);

CREATE INDEX idx_task_defer_history_assignment_time
    ON learn_task_defer_history (assignment_id, occurred_at, id);

ALTER TABLE learn_task_assignment_event
    MODIFY COLUMN operator_user_id BIGINT NULL;

ALTER TABLE learn_task_assignment_event
    DROP CONSTRAINT ck_task_assignment_event_type;

ALTER TABLE learn_task_assignment_event
    ADD CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEW_APPROVED', 'REVIEWER_TRANSFERRED', 'EXEMPTED',
            'POINT_CORRECTED', 'MARKED_NEEDS_IMPROVEMENT')
    );

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646511, 'TASK_ASSIGNMENT_DEFER', '顺延学习任务',
    'OPERATION', 'WEB', NULL, 157, 'ENABLED',
    '家长、教师或机构管理员在各自对象范围内顺延待优化任务。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646512, 1874244142494646276, 1874244142494646511),
    (1874244142494646513, 1874244142494646275, 1874244142494646511),
    (1874244142494646514, 1874244142494646277, 1874244142494646511);

UPDATE sys_feature_toggle
SET description = '控制任务草稿、发布、固定计划、学生执行、图片打卡、业务审核、待优化与顺延能力。',
    updated_at = CURRENT_TIMESTAMP
WHERE feature_code = 'LEARNING_TASK_MANAGEMENT'
  AND scope_key = 'GLOBAL';
