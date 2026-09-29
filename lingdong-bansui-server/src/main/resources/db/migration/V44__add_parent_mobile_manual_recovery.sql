-- V44：家长原手机号不可用时的机构人工核验换绑审计与准入配置。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

CREATE TABLE auth_parent_mobile_manual_recovery (
    id BIGINT NOT NULL,
    parent_user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    old_mobile_digest CHAR(64) NOT NULL,
    new_mobile_digest CHAR(64) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    recovered_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_parent_mobile_manual_recovery_client
        CHECK (client_type IN ('WEB', 'MINIAPP')),
    CONSTRAINT fk_parent_mobile_manual_recovery_parent
        FOREIGN KEY (parent_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_parent_mobile_manual_recovery_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_parent_mobile_manual_recovery_organization
        FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_parent_mobile_manual_recovery_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_parent_mobile_manual_recovery_parent_time
    ON auth_parent_mobile_manual_recovery (parent_user_id, recovered_at, id);

CREATE INDEX idx_parent_mobile_manual_recovery_student_time
    ON auth_parent_mobile_manual_recovery (student_id, recovered_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646555, 'PARENT_MOBILE_MANUAL_RECOVERY', '家长手机号人工核验换绑',
    'GLOBAL', 'GLOBAL', 'DISABLED', 1,
    '控制机构管理员在组织范围内核验家长身份并换绑新手机号的 Web 与小程序操作。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646556, 'PARENT_MOBILE_MANUAL_RECOVERY_MANAGE', '人工核验换绑家长手机号',
    'OPERATION', 'BOTH', NULL, 77, 'ENABLED',
    '机构管理员在活动学生和组织数据范围内核验家长身份并换绑新手机号。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646557, 1874244142494646275, 1874244142494646556);
