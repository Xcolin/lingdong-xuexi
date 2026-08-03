CREATE TABLE sys_cache_operation_log (
    id BIGINT NOT NULL PRIMARY KEY,
    operation_code VARCHAR(36) NOT NULL,
    task_id BIGINT,
    cache_domain VARCHAR(32) NOT NULL,
    operation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    impact_description VARCHAR(1000) NOT NULL,
    requested_by BIGINT NOT NULL,
    executed_by BIGINT,
    failure_message VARCHAR(1000),
    executed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_cache_operation_code UNIQUE (operation_code),
    CONSTRAINT uk_sys_cache_operation_task UNIQUE (task_id),
    CONSTRAINT fk_sys_cache_operation_task FOREIGN KEY (task_id) REFERENCES sys_system_task (id),
    CONSTRAINT fk_sys_cache_operation_requester FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_cache_operation_executor FOREIGN KEY (executed_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_cache_operation_created_at ON sys_cache_operation_log (created_at);
CREATE INDEX idx_sys_cache_operation_domain_status ON sys_cache_operation_log (cache_domain, status);
