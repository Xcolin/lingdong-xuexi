-- V23：学生任务执行、打卡、审核驳回与审核人转交基础。
-- 审核通过与积分入账将在后续迁移中原子实现，本迁移不开放完成状态写入口。

ALTER TABLE learn_task_assignment
    DROP CONSTRAINT ck_learn_task_assignment_status;

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT ck_learn_task_assignment_status CHECK (
        current_status IN ('PENDING_CLAIM', 'IN_PROGRESS', 'PENDING_REVIEW',
            'NEEDS_IMPROVEMENT', 'EXEMPT', 'COMPLETED')
    );

ALTER TABLE learn_task_assignment
    ADD COLUMN last_transition_at TIMESTAMP NULL;

ALTER TABLE learn_task_assignment
    ADD COLUMN version_no INT NOT NULL DEFAULT 0;

CREATE TABLE learn_task_assignment_event (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    reason VARCHAR(500) NULL,
    event_details VARCHAR(1000) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_assignment_event_assignment
        FOREIGN KEY (assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_task_assignment_event_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEWER_TRANSFERRED', 'EXEMPTED')
    )
);

CREATE INDEX idx_task_assignment_event_assignment_time
    ON learn_task_assignment_event (assignment_id, occurred_at, id);
CREATE INDEX idx_task_assignment_event_operator_time
    ON learn_task_assignment_event (operator_user_id, occurred_at);

CREATE TABLE learn_task_pause (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    pause_type VARCHAR(16) NOT NULL,
    started_by_user_id BIGINT NOT NULL,
    started_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    resumed_at TIMESTAMP NULL,
    close_type VARCHAR(16) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_pause_assignment
        FOREIGN KEY (assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_task_pause_started_by
        FOREIGN KEY (started_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_pause_type CHECK (pause_type IN ('EMOTION', 'DIFFICULTY')),
    CONSTRAINT ck_task_pause_close_type CHECK (
        close_type IS NULL OR close_type IN ('MANUAL', 'EXPIRED', 'TERMINATED')
    ),
    CONSTRAINT ck_task_pause_time CHECK (expires_at > started_at)
);

CREATE INDEX idx_task_pause_assignment_active
    ON learn_task_pause (assignment_id, resumed_at, expires_at);

CREATE TABLE learn_task_checkin (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    submission_no INT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    submitted_by_user_id BIGINT NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    review_comment VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_checkin_submission UNIQUE (assignment_id, submission_no),
    CONSTRAINT fk_task_checkin_assignment
        FOREIGN KEY (assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_task_checkin_submitted_by
        FOREIGN KEY (submitted_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_checkin_reviewed_by
        FOREIGN KEY (reviewed_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_checkin_submission_no CHECK (submission_no > 0),
    CONSTRAINT ck_task_checkin_status CHECK (status IN ('SUBMITTED', 'REJECTED'))
);

CREATE INDEX idx_task_checkin_assignment_status
    ON learn_task_checkin (assignment_id, status, submission_no);
CREATE INDEX idx_task_checkin_reviewer_time
    ON learn_task_checkin (reviewed_by_user_id, reviewed_at);

CREATE TABLE learn_task_reviewer_transfer (
    id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    from_reviewer_user_id BIGINT NOT NULL,
    to_reviewer_user_id BIGINT NOT NULL,
    transferred_by_user_id BIGINT NOT NULL,
    transfer_reason VARCHAR(500) NOT NULL,
    transferred_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_reviewer_transfer_assignment
        FOREIGN KEY (assignment_id) REFERENCES learn_task_assignment (id),
    CONSTRAINT fk_task_reviewer_transfer_from
        FOREIGN KEY (from_reviewer_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_reviewer_transfer_to
        FOREIGN KEY (to_reviewer_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_reviewer_transfer_operator
        FOREIGN KEY (transferred_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_reviewer_transfer_distinct
        CHECK (from_reviewer_user_id <> to_reviewer_user_id)
);

CREATE INDEX idx_task_reviewer_transfer_assignment_time
    ON learn_task_reviewer_transfer (assignment_id, transferred_at);
CREATE INDEX idx_task_reviewer_transfer_to_time
    ON learn_task_reviewer_transfer (to_reviewer_user_id, transferred_at);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646365, 'TASK_ASSIGNMENT_EXECUTE_SELF', '执行本人学习任务',
        'OPERATION', 'MINIAPP', NULL, 130, 'ENABLED',
        '学生在小程序认领、暂停、恢复、放弃和提交本人任务打卡。'),
    (1874244142494646366, 'TASK_ASSIGNMENT_REVIEW', '审核学习任务',
        'OPERATION', 'WEB', NULL, 140, 'ENABLED',
        '业务审核人查询本人待办、驳回打卡或转交审核责任。'),
    (1874244142494646367, 'TASK_ASSIGNMENT_EXEMPT', '设置学习任务免执行',
        'OPERATION', 'WEB', NULL, 150, 'ENABLED',
        '家长、机构管理员或教师在各自对象范围内设置任务免执行。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646368, 1874244142494646278, 1874244142494646365),
    (1874244142494646369, 1874244142494646276, 1874244142494646366),
    (1874244142494646370, 1874244142494646276, 1874244142494646367),
    (1874244142494646371, 1874244142494646275, 1874244142494646366),
    (1874244142494646372, 1874244142494646275, 1874244142494646367),
    (1874244142494646373, 1874244142494646277, 1874244142494646366),
    (1874244142494646374, 1874244142494646277, 1874244142494646367);

UPDATE sys_feature_toggle
SET description = '控制任务草稿、发布、班级关系、学生任务读取、执行和业务审核基础能力。',
    updated_at = CURRENT_TIMESTAMP
WHERE feature_code = 'LEARNING_TASK_MANAGEMENT'
  AND scope_key = 'GLOBAL';
