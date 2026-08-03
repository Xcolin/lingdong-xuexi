CREATE TABLE sys_user_permission (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    effect VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_permission_assignment UNIQUE (user_id, permission_id),
    CONSTRAINT fk_sys_user_permission_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);
