-- V29：固定任务积分衰减、沉睡状态、提醒待投递和清零审计基础。
-- 所有新增主键由应用层雪花算法生成或确定性复用学生雪花标识。

-- 同一固定任务允许同一学生按不同计划自然日形成独立执行实例。
ALTER TABLE learn_task_assignment
    DROP INDEX uk_learn_task_assignment_task_student;

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT uk_learn_task_assignment_task_student_date
        UNIQUE (task_id, student_id, scheduled_date);

CREATE TABLE growth_point_decay_rule (
    id BIGINT NOT NULL,
    start_streak_day INT NOT NULL,
    decay_percent INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    effective_from TIMESTAMP NOT NULL,
    effective_to TIMESTAMP NULL,
    version_no INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_point_decay_rule_start_version
        UNIQUE (start_streak_day, version_no),
    CONSTRAINT ck_growth_point_decay_rule_day CHECK (start_streak_day >= 2),
    CONSTRAINT ck_growth_point_decay_rule_percent CHECK (decay_percent BETWEEN 1 AND 40),
    CONSTRAINT ck_growth_point_decay_rule_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_growth_point_decay_rule_effective CHECK (
        effective_to IS NULL OR effective_to > effective_from
    )
);

CREATE INDEX idx_growth_point_decay_rule_effective
    ON growth_point_decay_rule (status, effective_from, effective_to, start_streak_day);

CREATE TABLE growth_point_dormancy_state (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    last_activity_at TIMESTAMP NOT NULL,
    reminder_due_at TIMESTAMP NOT NULL,
    clear_due_at TIMESTAMP NOT NULL,
    last_reminder_created_at TIMESTAMP NULL,
    last_cleared_at TIMESTAMP NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_point_dormancy_state_student UNIQUE (student_id),
    CONSTRAINT fk_growth_point_dormancy_state_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT ck_growth_point_dormancy_state_due CHECK (
        reminder_due_at > last_activity_at AND clear_due_at > reminder_due_at
    )
);

CREATE INDEX idx_growth_point_dormancy_state_reminder
    ON growth_point_dormancy_state (reminder_due_at, last_reminder_created_at, student_id);
CREATE INDEX idx_growth_point_dormancy_state_clear
    ON growth_point_dormancy_state (clear_due_at, last_cleared_at, student_id);

-- 历史学生从迁移时刻开始新一轮计时，避免上线时对既有账户立即清零。
INSERT INTO growth_point_dormancy_state (
    id, student_id, last_activity_at, reminder_due_at, clear_due_at,
    version_no, created_at, updated_at
)
SELECT student.id, student.id, CURRENT_TIMESTAMP,
       TIMESTAMPADD(DAY, 27, CURRENT_TIMESTAMP),
       TIMESTAMPADD(DAY, 30, CURRENT_TIMESTAMP),
       0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM edu_student student;

CREATE TABLE growth_point_dormancy_notice (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    primary_parent_user_id BIGINT NULL,
    activity_baseline_at TIMESTAMP NOT NULL,
    clear_due_at TIMESTAMP NOT NULL,
    delivery_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    delivered_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_point_dormancy_notice_cycle
        UNIQUE (student_id, activity_baseline_at),
    CONSTRAINT fk_growth_point_dormancy_notice_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_growth_point_dormancy_notice_parent
        FOREIGN KEY (primary_parent_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_growth_point_dormancy_notice_due CHECK (
        clear_due_at > activity_baseline_at
    ),
    CONSTRAINT ck_growth_point_dormancy_notice_status CHECK (
        delivery_status IN ('PENDING', 'DELIVERED', 'NO_RECIPIENT', 'FAILED')
    )
);

CREATE INDEX idx_growth_point_dormancy_notice_delivery
    ON growth_point_dormancy_notice (delivery_status, created_at, id);

ALTER TABLE growth_point_ledger
    ADD COLUMN source_task_id BIGINT NULL;
ALTER TABLE growth_point_ledger
    ADD COLUMN source_dormancy_notice_id BIGINT NULL;
ALTER TABLE growth_point_ledger
    ADD COLUMN base_points_snapshot INT NULL;
ALTER TABLE growth_point_ledger
    ADD COLUMN decay_percent INT NULL;
ALTER TABLE growth_point_ledger
    ADD COLUMN streak_days INT NULL;
ALTER TABLE growth_point_ledger
    ADD COLUMN decay_rule_id BIGINT NULL;

UPDATE growth_point_ledger ledger
SET source_task_id = (
    SELECT assignment.task_id
    FROM learn_task_assignment assignment
    WHERE assignment.id = ledger.source_assignment_id
)
WHERE ledger.source_assignment_id IS NOT NULL;

UPDATE growth_point_ledger ledger
SET base_points_snapshot = (
        SELECT task.base_points
        FROM learn_task_assignment assignment
        JOIN learn_task task ON task.id = assignment.task_id
        WHERE assignment.id = ledger.source_assignment_id
    ),
    decay_percent = 0,
    streak_days = 1
WHERE ledger.change_type = 'TASK_REWARD';

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT fk_growth_point_ledger_task
        FOREIGN KEY (source_task_id) REFERENCES learn_task (id);
ALTER TABLE growth_point_ledger
    ADD CONSTRAINT fk_growth_point_ledger_dormancy_notice
        FOREIGN KEY (source_dormancy_notice_id) REFERENCES growth_point_dormancy_notice (id);
ALTER TABLE growth_point_ledger
    ADD CONSTRAINT fk_growth_point_ledger_decay_rule
        FOREIGN KEY (decay_rule_id) REFERENCES growth_point_decay_rule (id);
ALTER TABLE growth_point_ledger
    ADD CONSTRAINT uk_growth_point_ledger_dormancy_notice
        UNIQUE (source_dormancy_notice_id);

ALTER TABLE growth_point_ledger
    DROP CONSTRAINT ck_growth_point_ledger_task_source;

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_task_source CHECK (
        change_type <> 'TASK_REWARD'
        OR (source_assignment_id IS NOT NULL
            AND source_task_id IS NOT NULL
            AND source_type IS NOT NULL
            AND reviewer_user_id IS NOT NULL
            AND base_points_snapshot IN (10, 20, 30)
            AND decay_percent BETWEEN 0 AND 40
            AND streak_days > 0
            AND amount > 0
            AND amount <= base_points_snapshot
            AND available_delta = amount)
    );

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_decay_audit CHECK (
        change_type = 'TASK_REWARD'
        OR (base_points_snapshot IS NULL AND decay_percent IS NULL
            AND streak_days IS NULL AND decay_rule_id IS NULL)
    );

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_dormancy_clear CHECK (
        (change_type = 'DORMANCY_CLEAR'
            AND source_dormancy_notice_id IS NOT NULL
            AND source_assignment_id IS NULL
            AND source_task_id IS NULL
            AND source_exchange_id IS NULL
            AND source_type IS NULL
            AND source_organization_id IS NULL
            AND reviewer_user_id IS NULL
            AND amount = 0
            AND available_delta < 0
            AND correction_of_id IS NULL)
        OR (change_type <> 'DORMANCY_CLEAR' AND source_dormancy_notice_id IS NULL)
    );

INSERT INTO growth_point_decay_rule (
    id, start_streak_day, decay_percent, status, effective_from, version_no
) VALUES
    (1874244142494646401, 8, 20, 'ENABLED', '2000-01-01 00:00:00', 1),
    (1874244142494646402, 16, 40, 'ENABLED', '2000-01-01 00:00:00', 1);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646400, 'POINT_LIFECYCLE', '积分生命周期',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制固定任务积分阶梯衰减、沉睡提醒待投递和可用积分清零。'
);
