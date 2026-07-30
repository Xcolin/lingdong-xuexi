CREATE TABLE sys_role_data_scope (
    role_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, organization_id),
    CONSTRAINT fk_sys_role_data_scope_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_data_scope_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);
