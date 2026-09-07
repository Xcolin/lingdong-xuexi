-- V39：设备会话自主管理与站内账号安全事件。
-- 所有主键均由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE auth_security_event (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    event_type VARCHAR(32) NOT NULL,
    risk_level VARCHAR(16) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    device_fingerprint_hash CHAR(64) NOT NULL,
    event_scope_key VARCHAR(96) NOT NULL,
    status VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_security_event_scope
        UNIQUE (user_id, event_type, event_scope_key),
    CONSTRAINT fk_auth_security_event_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_auth_security_event_session
        FOREIGN KEY (session_id) REFERENCES auth_device_session (id),
    CONSTRAINT ck_auth_security_event_type
        CHECK (event_type IN ('NEW_DEVICE_LOGIN', 'DEVICE_REVOKED', 'ALL_SESSIONS_REVOKED')),
    CONSTRAINT ck_auth_security_event_risk
        CHECK (risk_level IN ('INFO', 'WARNING')),
    CONSTRAINT ck_auth_security_event_status
        CHECK (status IN ('UNREAD', 'READ')),
    CONSTRAINT ck_auth_security_event_read_state
        CHECK ((status = 'UNREAD' AND read_at IS NULL)
            OR (status = 'READ' AND read_at IS NOT NULL))
);

CREATE INDEX idx_auth_security_event_user_status_time
    ON auth_security_event (user_id, status, occurred_at);

CREATE INDEX idx_auth_session_user_client_device
    ON auth_device_session (user_id, client_type, device_id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646547, 'ACCOUNT_SECURITY_MANAGEMENT', '账号安全管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制活动设备自主管理和站内账号安全提醒。'
);
