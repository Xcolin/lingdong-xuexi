-- Registers third-party and outbound interface metadata without storing credentials or payloads.
CREATE TABLE sys_interface_service (
    id BIGINT NOT NULL PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    direction VARCHAR(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    caller_name VARCHAR(100) NOT NULL,
    authorization_scope VARCHAR(32) NOT NULL,
    authorization_scope_value VARCHAR(128),
    owner_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_interface_service_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
);

-- Stores a single high-risk task proposal before the related service mutation takes effect.
CREATE TABLE sys_interface_service_change (
    id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    service_id BIGINT,
    change_type VARCHAR(32) NOT NULL,
    service_name VARCHAR(100),
    direction VARCHAR(16),
    purpose VARCHAR(32),
    caller_name VARCHAR(100),
    authorization_scope VARCHAR(32),
    authorization_scope_value VARCHAR(128),
    owner_id BIGINT,
    target_status VARCHAR(16),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_interface_service_change_task UNIQUE (task_id),
    CONSTRAINT fk_sys_interface_service_change_task FOREIGN KEY (task_id) REFERENCES sys_system_task (id),
    CONSTRAINT fk_sys_interface_service_change_service FOREIGN KEY (service_id) REFERENCES sys_interface_service (id),
    CONSTRAINT fk_sys_interface_service_change_owner FOREIGN KEY (owner_id) REFERENCES sys_user (id)
);

-- Keeps only the minimum call outcome needed for support and auditing.
CREATE TABLE sys_interface_call_log (
    id BIGINT NOT NULL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    caller_name VARCHAR(100) NOT NULL,
    result VARCHAR(16) NOT NULL,
    error_summary VARCHAR(1000),
    trace_id VARCHAR(64),
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sys_interface_call_log_service FOREIGN KEY (service_id) REFERENCES sys_interface_service (id)
);

CREATE INDEX idx_sys_interface_service_status_purpose
    ON sys_interface_service (status, purpose);
CREATE INDEX idx_sys_interface_service_change_service
    ON sys_interface_service_change (service_id);
CREATE INDEX idx_sys_interface_call_log_service_occurred_at
    ON sys_interface_call_log (service_id, occurred_at);
