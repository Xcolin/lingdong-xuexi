CREATE TABLE sys_feature_toggle_change (
    id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    feature_code VARCHAR(64) NOT NULL,
    target_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_feature_toggle_change_task UNIQUE (task_id),
    CONSTRAINT fk_sys_feature_toggle_change_task FOREIGN KEY (task_id) REFERENCES sys_system_task (id)
);
