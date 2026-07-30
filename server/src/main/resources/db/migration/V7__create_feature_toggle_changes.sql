CREATE TABLE sys_feature_toggle_change (
    task_id BIGINT PRIMARY KEY,
    feature_code VARCHAR(64) NOT NULL,
    target_status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_feature_toggle_change_task FOREIGN KEY (task_id) REFERENCES sys_system_task (id)
);
