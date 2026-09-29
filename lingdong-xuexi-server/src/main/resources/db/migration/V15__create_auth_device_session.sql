-- 保存可撤销的设备会话与令牌摘要，原始访问凭证和刷新凭证不入库。
CREATE TABLE auth_device_session (
    id BIGINT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    device_id VARCHAR(128) NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    access_token_hash CHAR(64) NOT NULL,
    refresh_token_hash CHAR(64) NOT NULL,
    access_expires_at TIMESTAMP NOT NULL,
    refresh_expires_at TIMESTAMP NOT NULL,
    status VARCHAR(16) NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    signed_out_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_auth_session_access_token_hash UNIQUE (access_token_hash),
    CONSTRAINT uk_auth_session_refresh_token_hash UNIQUE (refresh_token_hash),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_auth_session_user_status ON auth_device_session (user_id, status);
