-- 用途：为每日固定任务增加草稿配置和独立调度计划。
-- 来源：docs/superpowers/specs/2026-08-08-lingdong-recurring-task-design.md。
-- 所有新增主键由应用层雪花算法生成，禁止自增。

ALTER TABLE learn_task
    ADD COLUMN recurrence_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE learn_task
    ADD COLUMN recurrence_end_date DATE NULL;

ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_recurrence_config
        CHECK ((recurrence_enabled = TRUE)
            OR (recurrence_enabled = FALSE AND recurrence_end_date IS NULL));

ALTER TABLE learn_task
    ADD CONSTRAINT ck_learn_task_recurrence_end_date
        CHECK (recurrence_end_date IS NULL OR recurrence_end_date >= scheduled_date);

CREATE TABLE learn_task_recurrence (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    frequency_type VARCHAR(16) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    next_generation_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    stopped_by_user_id BIGINT NULL,
    stopped_at TIMESTAMP NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_learn_task_recurrence_task UNIQUE (task_id),
    CONSTRAINT fk_learn_task_recurrence_task
        FOREIGN KEY (task_id) REFERENCES learn_task (id),
    CONSTRAINT fk_learn_task_recurrence_stopped_by
        FOREIGN KEY (stopped_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_learn_task_recurrence_frequency
        CHECK (frequency_type = 'DAILY'),
    CONSTRAINT ck_learn_task_recurrence_dates
        CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_learn_task_recurrence_status
        CHECK (status IN ('ACTIVE', 'COMPLETED', 'STOPPED')),
    CONSTRAINT ck_learn_task_recurrence_stop_audit
        CHECK ((status = 'STOPPED' AND stopped_by_user_id IS NOT NULL AND stopped_at IS NOT NULL)
            OR (status IN ('ACTIVE', 'COMPLETED') AND stopped_by_user_id IS NULL AND stopped_at IS NULL))
);

CREATE INDEX idx_learn_task_recurrence_due
    ON learn_task_recurrence (status, next_generation_date, id);
