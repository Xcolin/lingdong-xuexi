-- V45：学生账号注销审计、功能开关和机构管理员权限。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

CREATE TABLE auth_student_account_cancellation (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    reason VARCHAR(200) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    cancelled_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_student_account_cancellation_student UNIQUE (student_id),
    CONSTRAINT ck_student_account_cancellation_client
        CHECK (client_type IN ('WEB', 'MINIAPP')),
    CONSTRAINT fk_student_account_cancellation_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_student_account_cancellation_user
        FOREIGN KEY (student_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_student_account_cancellation_organization
        FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_student_account_cancellation_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_student_account_cancellation_organization_time
    ON auth_student_account_cancellation (organization_id, cancelled_at, id);

CREATE INDEX idx_student_account_cancellation_operator_time
    ON auth_student_account_cancellation (operator_user_id, cancelled_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646558, 'STUDENT_ACCOUNT_CANCELLATION', '学生账号注销',
    'GLOBAL', 'GLOBAL', 'DISABLED', 1,
    '控制最近停用入学机构管理员注销无活动机构和无活动家长关系的学生账号。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646559, 'STUDENT_ACCOUNT_CANCELLATION_MANAGE', '注销学生账号',
    'OPERATION', 'BOTH', NULL, 78, 'ENABLED',
    '机构管理员注销最近停用入学组织范围内且已解除全部活动关系的学生账号。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646560, 1874244142494646275, 1874244142494646559);

