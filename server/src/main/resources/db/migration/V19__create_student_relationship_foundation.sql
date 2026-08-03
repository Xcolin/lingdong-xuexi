-- V19：学生档案及其家长、机构关系基础。
-- 所有主键由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE edu_student (
    id BIGINT NOT NULL,
    student_name VARCHAR(64) NOT NULL,
    grade_code VARCHAR(64) NULL,
    student_user_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_edu_student_user UNIQUE (student_user_id),
    CONSTRAINT fk_edu_student_user FOREIGN KEY (student_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_edu_student_name ON edu_student (student_name);
CREATE INDEX idx_edu_student_status ON edu_student (status);

CREATE TABLE edu_parent_student (
    id BIGINT NOT NULL,
    parent_user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    relation_role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    primary_scope_key VARCHAR(16) NOT NULL,
    bound_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    unbound_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_edu_parent_student_pair UNIQUE (parent_user_id, student_id),
    CONSTRAINT uk_edu_parent_student_primary UNIQUE (student_id, primary_scope_key),
    CONSTRAINT fk_edu_parent_student_parent FOREIGN KEY (parent_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_edu_parent_student_student FOREIGN KEY (student_id) REFERENCES edu_student (id)
);

CREATE INDEX idx_edu_parent_student_parent_status ON edu_parent_student (parent_user_id, status);
CREATE INDEX idx_edu_parent_student_student_status ON edu_parent_student (student_id, status);

CREATE TABLE edu_student_organization (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    relation_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_edu_student_organization_relation UNIQUE (student_id, organization_id, relation_type),
    CONSTRAINT fk_edu_student_organization_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_edu_student_organization_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_edu_student_organization_org_status ON edu_student_organization (organization_id, status);
CREATE INDEX idx_edu_student_organization_student_status ON edu_student_organization (student_id, status);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description
) VALUES
    (1874244142494646324, 'STUDENT_CREATE', '创建学生档案', 'OPERATION', 'WEB', NULL, 10, 'ENABLED', '家长或直管机构管理员创建学生档案及初始关系。'),
    (1874244142494646325, 'STUDENT_READ', '查询学生档案', 'OPERATION', 'WEB', NULL, 20, 'ENABLED', '按角色和直接关联范围查询学生档案。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646326, 1874244142494646277, 1874244142494646324),
    (1874244142494646327, 1874244142494646275, 1874244142494646324),
    (1874244142494646328, 1874244142494646273, 1874244142494646325),
    (1874244142494646329, 1874244142494646277, 1874244142494646325),
    (1874244142494646330, 1874244142494646275, 1874244142494646325);
