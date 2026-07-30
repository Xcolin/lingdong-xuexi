CREATE TABLE sys_organization_type (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type_code VARCHAR(32) NOT NULL,
    type_name VARCHAR(32) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_organization_type_code UNIQUE (type_code),
    CONSTRAINT uk_sys_organization_type_name UNIQUE (type_name)
);

INSERT INTO sys_organization_type (type_code, type_name, built_in, status, sort_order) VALUES
    ('REGION', '区域', 1, 'ENABLED', 10),
    ('SCHOOL', '学校', 1, 'ENABLED', 20),
    ('CAMPUS', '校区', 1, 'ENABLED', 30),
    ('GRADE', '年级', 1, 'ENABLED', 40),
    ('CLASS', '班级', 1, 'ENABLED', 50);

ALTER TABLE sys_organization
    ADD COLUMN parent_scope_key VARCHAR(32) NOT NULL DEFAULT 'ROOT';

ALTER TABLE sys_organization
    ADD CONSTRAINT uk_sys_organization_sibling_name UNIQUE (parent_scope_key, organization_name);

ALTER TABLE sys_organization
    ADD CONSTRAINT fk_sys_organization_type
    FOREIGN KEY (organization_type) REFERENCES sys_organization_type (type_code);
