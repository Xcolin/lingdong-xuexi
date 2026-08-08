-- V24：任务审核通过、学生积分账户与不可变积分台账基础。

ALTER TABLE learn_task_checkin
    DROP CONSTRAINT ck_task_checkin_status;

ALTER TABLE learn_task_checkin
    ADD CONSTRAINT ck_task_checkin_status CHECK (
        status IN ('SUBMITTED', 'REJECTED', 'APPROVED')
    );

ALTER TABLE learn_task_assignment_event
    DROP CONSTRAINT ck_task_assignment_event_type;

ALTER TABLE learn_task_assignment_event
    ADD CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEW_APPROVED', 'REVIEWER_TRANSFERRED', 'EXEMPTED')
    );

CREATE TABLE growth_point_account (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    total_points BIGINT NOT NULL DEFAULT 0,
    available_points BIGINT NOT NULL DEFAULT 0,
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_point_account_student UNIQUE (student_id),
    CONSTRAINT fk_growth_point_account_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT ck_growth_point_account_total CHECK (total_points >= 0),
    CONSTRAINT ck_growth_point_account_available CHECK (available_points >= 0)
);

CREATE INDEX idx_growth_point_account_student
    ON growth_point_account (student_id);

-- 账户与学生是一对一关系，复用学生雪花标识可在迁移中确定性回填。
INSERT INTO growth_point_account (
    id, student_id, total_points, available_points, version_no, created_at, updated_at
)
SELECT student.id, student.id, 0, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM edu_student student;

CREATE TABLE growth_point_ledger (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    source_assignment_id BIGINT NULL,
    source_type VARCHAR(16) NULL,
    source_organization_id BIGINT NULL,
    change_type VARCHAR(32) NOT NULL,
    amount BIGINT NOT NULL,
    available_delta BIGINT NOT NULL,
    reviewer_user_id BIGINT NULL,
    occurred_at TIMESTAMP NOT NULL,
    correction_of_id BIGINT NULL,
    remark VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_point_ledger_task_reward
        UNIQUE (source_assignment_id, change_type),
    CONSTRAINT fk_growth_point_ledger_account
        FOREIGN KEY (account_id) REFERENCES growth_point_account (id),
    CONSTRAINT fk_growth_point_ledger_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_growth_point_ledger_assignment
        FOREIGN KEY (source_assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_growth_point_ledger_source_organization
        FOREIGN KEY (source_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_growth_point_ledger_reviewer
        FOREIGN KEY (reviewer_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_growth_point_ledger_correction
        FOREIGN KEY (correction_of_id) REFERENCES growth_point_ledger (id),
    CONSTRAINT ck_growth_point_ledger_source_type CHECK (
        source_type IS NULL OR source_type IN ('FAMILY', 'ORGANIZATION', 'TEACHER')
    ),
    CONSTRAINT ck_growth_point_ledger_change_type CHECK (
        change_type IN ('TASK_REWARD', 'REDEMPTION', 'DORMANCY_CLEAR', 'CORRECTION')
    ),
    CONSTRAINT ck_growth_point_ledger_amount CHECK (amount <> 0),
    CONSTRAINT ck_growth_point_ledger_task_source CHECK (
        change_type <> 'TASK_REWARD'
        OR (source_assignment_id IS NOT NULL AND source_type IS NOT NULL
            AND reviewer_user_id IS NOT NULL AND amount > 0 AND available_delta = amount)
    )
);

CREATE INDEX idx_growth_point_ledger_student_time
    ON growth_point_ledger (student_id, occurred_at, id);
CREATE INDEX idx_growth_point_ledger_account_time
    ON growth_point_ledger (account_id, occurred_at, id);
CREATE INDEX idx_growth_point_ledger_assignment
    ON growth_point_ledger (source_assignment_id);
