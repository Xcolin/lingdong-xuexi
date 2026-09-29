ALTER TABLE sys_user
    ADD CONSTRAINT uk_sys_user_mobile UNIQUE (mobile);

CREATE TABLE sys_user_organization (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    associated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_organization_assignment UNIQUE (user_id, organization_id),
    CONSTRAINT fk_sys_user_organization_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_organization_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_user_organization_organization_id ON sys_user_organization (organization_id);
