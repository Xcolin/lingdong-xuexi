-- V50：身份权限变更不可变审计及 Web 查询权限。
-- 审计表只记录标识、事件和非敏感状态，不记录姓名、手机号、密码或令牌。

CREATE TABLE sys_iam_change_audit (
    id BIGINT NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    operator_id BIGINT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    related_id BIGINT NULL,
    organization_id BIGINT NULL,
    before_value VARCHAR(128) NULL,
    after_value VARCHAR(128) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_sys_iam_change_audit_event CHECK (event_type IN (
        'USER_CREATE', 'USER_STATUS_CHANGE', 'USER_ORGANIZATION_ASSOCIATE', 'USER_ROLE_ASSIGN',
        'ROLE_CREATE', 'PERMISSION_CREATE', 'ROLE_PERMISSION_CONFIGURE', 'ROLE_PERMISSION_REMOVE',
        'USER_PERMISSION_CONFIGURE', 'USER_PERMISSION_REMOVE', 'ROLE_DATA_SCOPE_ADD',
        'ORGANIZATION_ADMIN_ASSIGN'
    )),
    CONSTRAINT ck_sys_iam_change_audit_target CHECK (target_type IN (
        'USER', 'ROLE', 'PERMISSION', 'ROLE_PERMISSION', 'USER_PERMISSION',
        'ROLE_DATA_SCOPE', 'ORGANIZATION_ADMIN', 'USER_ORGANIZATION', 'USER_ROLE'
    ))
);

CREATE INDEX idx_sys_iam_audit_occurred ON sys_iam_change_audit (occurred_at, id);
CREATE INDEX idx_sys_iam_audit_target ON sys_iam_change_audit (target_type, target_id, occurred_at);
CREATE INDEX idx_sys_iam_audit_operator ON sys_iam_change_audit (operator_id, occurred_at);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646580, 'IAM_AUDIT_READ', '查询身份权限变更审计',
    'OPERATION', 'WEB', NULL, 120, 'ENABLED', '分页查询用户、角色、权限和数据范围的不可变变更审计。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES (
    1874244142494646581, 1874244142494646273, 1874244142494646580, 'ALLOW'
);
