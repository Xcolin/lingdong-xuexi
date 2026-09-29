-- V42：家长手机号换绑审计与账号注销前置状态机。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

CREATE TABLE auth_parent_mobile_change (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    old_mobile_digest CHAR(64) NOT NULL,
    new_mobile_digest CHAR(64) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    changed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_parent_mobile_change_client CHECK (client_type IN ('WEB', 'MINIAPP')),
    CONSTRAINT fk_parent_mobile_change_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_parent_mobile_change_user_time
    ON auth_parent_mobile_change (user_id, changed_at, id);

CREATE TABLE auth_parent_account_cancellation (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    active_scope_key VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    cooling_ends_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    finalized_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_parent_account_cancellation_active UNIQUE (user_id, active_scope_key),
    CONSTRAINT ck_parent_account_cancellation_status CHECK (
        status IN ('COOLING_OFF', 'REVOKED', 'FINALIZED')
    ),
    CONSTRAINT ck_parent_account_cancellation_time CHECK (cooling_ends_at > requested_at),
    CONSTRAINT ck_parent_account_cancellation_state CHECK (
        (status = 'COOLING_OFF' AND active_scope_key = 'ACTIVE'
            AND revoked_at IS NULL AND finalized_at IS NULL)
        OR (status = 'REVOKED' AND active_scope_key <> 'ACTIVE'
            AND revoked_at IS NOT NULL AND finalized_at IS NULL)
        OR (status = 'FINALIZED' AND active_scope_key <> 'ACTIVE'
            AND finalized_at IS NOT NULL)
    ),
    CONSTRAINT fk_parent_account_cancellation_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_parent_account_cancellation_user_time
    ON auth_parent_account_cancellation (user_id, requested_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646552, 'PARENT_ACCOUNT_LIFECYCLE', '家长账号生命周期',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制家长手机号换绑、注销前置申请和冷静期撤销的 Web 与小程序操作。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646553, 'PARENT_ACCOUNT_LIFECYCLE_MANAGE', '管理家长本人账号生命周期',
    'OPERATION', 'BOTH', NULL, 76, 'ENABLED',
    '家长验证本人手机号后执行换绑、申请注销或撤销冷静期申请。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646554, 1874244142494646277, 1874244142494646553);
