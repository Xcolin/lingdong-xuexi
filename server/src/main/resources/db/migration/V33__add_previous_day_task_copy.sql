-- V33：增加按学生复制昨日家庭任务的批次、条目、功能开关和最小权限。
ALTER TABLE learn_task
    DROP CONSTRAINT ck_learn_task_generation_type;

ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_generation_type CHECK (
        generation_type IN ('NORMAL', 'DEFERRED', 'COPIED')
    );

CREATE TABLE learn_task_copy_batch (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    source_date DATE NOT NULL,
    target_date DATE NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    confirm_duplicate_titles TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_copy_batch_student_date UNIQUE (student_id, target_date),
    CONSTRAINT fk_task_copy_batch_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_task_copy_batch_creator
        FOREIGN KEY (created_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_task_copy_batch_dates CHECK (target_date > source_date),
    CONSTRAINT ck_task_copy_batch_confirm CHECK (confirm_duplicate_titles IN (0, 1)),
    CONSTRAINT ck_task_copy_batch_status CHECK (
        status IN ('PROCESSING', 'COMPLETED', 'PARTIAL_FAILED', 'FAILED')
    ),
    CONSTRAINT ck_task_copy_batch_counts CHECK (
        total_count >= 0 AND success_count >= 0 AND failure_count >= 0
        AND success_count + failure_count <= total_count
    )
);

CREATE INDEX idx_task_copy_batch_creator_date
    ON learn_task_copy_batch (created_by_user_id, target_date, id);

CREATE TABLE learn_task_copy_item (
    id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    source_task_id BIGINT NOT NULL,
    target_task_id BIGINT NULL,
    task_title_snapshot VARCHAR(50) NOT NULL,
    status VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_copy_item_batch_source UNIQUE (batch_id, source_task_id),
    CONSTRAINT fk_task_copy_item_batch
        FOREIGN KEY (batch_id) REFERENCES learn_task_copy_batch (id),
    CONSTRAINT fk_task_copy_item_source_task
        FOREIGN KEY (source_task_id) REFERENCES learn_task (id),
    CONSTRAINT fk_task_copy_item_target_task
        FOREIGN KEY (target_task_id) REFERENCES learn_task (id),
    CONSTRAINT ck_task_copy_item_status CHECK (
        status IN ('PENDING', 'SUCCESS', 'FAILED')
    ),
    CONSTRAINT ck_task_copy_item_retry CHECK (retry_count >= 0)
);

CREATE INDEX idx_task_copy_item_batch_status
    ON learn_task_copy_item (batch_id, status, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key,
    status, built_in, description
) VALUES (
    1874244142494646515, 'COPY_PREVIOUS_DAY_TASK', '复制昨日任务',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制活动主家长按学生复制昨日家庭任务的预览、执行和失败重试能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646516, 'LEARNING_TASK_COPY_PREVIOUS_DAY', '复制孩子昨日任务',
    'OPERATION', 'WEB', NULL, 158, 'ENABLED',
    '活动主家长在 Web 按学生复制昨日可管理的家庭任务。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646517, 1874244142494646277, 1874244142494646516);
