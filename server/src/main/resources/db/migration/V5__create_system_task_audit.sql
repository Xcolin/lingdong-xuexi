CREATE TABLE sys_system_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_code VARCHAR(36) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    task_title VARCHAR(100) NOT NULL,
    task_description VARCHAR(1000) NOT NULL,
    impact_scope VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    submitted_by BIGINT NOT NULL,
    submitted_at TIMESTAMP,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    review_comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_system_task_code UNIQUE (task_code),
    CONSTRAINT fk_sys_system_task_submitter FOREIGN KEY (submitted_by) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_system_task_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_system_task_status ON sys_system_task (status);
CREATE INDEX idx_sys_system_task_submitter ON sys_system_task (submitted_by);
