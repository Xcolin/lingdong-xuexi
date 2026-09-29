-- V41：学生机构关系生命周期和不可变变更审计。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

CREATE TABLE edu_student_organization_change (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    from_organization_id BIGINT NULL,
    to_organization_id BIGINT NULL,
    reason VARCHAR(200) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_student_organization_change_type CHECK (
        change_type IN ('CLASS_ASSIGN', 'CLASS_TRANSFER', 'ENROLLMENT_DEACTIVATE')
    ),
    CONSTRAINT fk_student_organization_change_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_student_organization_change_from_org
        FOREIGN KEY (from_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_student_organization_change_to_org
        FOREIGN KEY (to_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_student_organization_change_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_student_organization_change_student_time
    ON edu_student_organization_change (student_id, occurred_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646549, 'STUDENT_ORGANIZATION_RELATIONSHIP', '学生机构关系管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制机构管理员查询、转班和停用学生机构关系的 Web 与小程序操作。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646550, 'STUDENT_ORGANIZATION_MANAGE', '管理学生机构关系',
    'OPERATION', 'BOTH', NULL, 75, 'ENABLED',
    '机构管理员在授权组织范围内查询、转班和停用学生机构关系。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646551, 1874244142494646275, 1874244142494646550);
