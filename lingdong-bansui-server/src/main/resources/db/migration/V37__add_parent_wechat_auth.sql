-- V37：家长微信授权身份的一对一绑定与独立功能开关。
-- 所有主键均由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE auth_parent_wechat_binding (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    union_id VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    bound_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_parent_wechat_binding_user UNIQUE (user_id),
    CONSTRAINT uk_auth_parent_wechat_binding_identity UNIQUE (app_id, open_id),
    CONSTRAINT fk_auth_parent_wechat_binding_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_auth_parent_wechat_binding_status CHECK (status IN ('ACTIVE'))
);

CREATE INDEX idx_auth_parent_wechat_binding_union
    ON auth_parent_wechat_binding (union_id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646533, 'PARENT_WECHAT_AUTH', '家长微信授权登录', 'GLOBAL', 'GLOBAL',
        'DISABLED', 1, '控制家长微信小程序授权、首次手机号绑定和后续快捷登录能力。');
