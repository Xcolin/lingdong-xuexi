CREATE TABLE sys_organization (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    organization_code VARCHAR(64) NOT NULL,
    organization_name VARCHAR(128) NOT NULL,
    organization_type VARCHAR(32) NOT NULL,
    organization_path VARCHAR(1024) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_organization_code UNIQUE (organization_code),
    CONSTRAINT fk_sys_organization_parent FOREIGN KEY (parent_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_organization_parent_id ON sys_organization (parent_id);

CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    mobile VARCHAR(32),
    password_hash VARCHAR(255),
    user_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE INDEX idx_sys_user_mobile ON sys_user (mobile);

CREATE TABLE sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(64) NOT NULL,
    role_type VARCHAR(16) NOT NULL,
    data_scope VARCHAR(16) NOT NULL,
    built_in TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    parent_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    description VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_permission_code UNIQUE (permission_code),
    CONSTRAINT fk_sys_permission_parent FOREIGN KEY (parent_id) REFERENCES sys_permission (id)
);

CREATE INDEX idx_sys_permission_parent_id ON sys_permission (parent_id);

CREATE TABLE sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    organization_id BIGINT,
    organization_scope_key VARCHAR(128) NOT NULL DEFAULT 'GLOBAL',
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_role_assignment UNIQUE (user_id, role_id, organization_scope_key),
    CONSTRAINT fk_sys_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_user_role_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_user_role_role_id ON sys_user_role (role_id);
CREATE INDEX idx_sys_user_role_organization_id ON sys_user_role (organization_id);

CREATE TABLE sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);

CREATE TABLE sys_organization_admin (
    organization_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT fk_sys_organization_admin_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_sys_organization_admin_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

INSERT INTO sys_role (
    role_code,
    role_name,
    role_type,
    data_scope,
    built_in,
    status,
    description
) VALUES
    ('SYS_ADMIN', '系统管理员', 'BUILT_IN', 'ALL', 1, 'ENABLED', '负责系统配置、角色权限和高风险系统任务发起。'),
    ('SYS_AUDITOR', '系统审核员', 'BUILT_IN', 'ALL', 1, 'ENABLED', '仅审核系统管理员提交的高风险系统任务。'),
    ('ORG_ADMIN', '机构（学校）管理员', 'BUILT_IN', 'SCHOOL', 1, 'ENABLED', '管理授权学校及其下级组织事务。'),
    ('TEACHER', '教师', 'BUILT_IN', 'CLASS', 1, 'ENABLED', '负责授权班级的学习任务与学情管理。'),
    ('PARENT', '家长', 'BUILT_IN', 'SELF', 1, 'ENABLED', '管理关联学生的家庭学习事务。'),
    ('STUDENT', '学生', 'BUILT_IN', 'SELF', 1, 'ENABLED', '执行学习任务并查看本人学习信息。');
