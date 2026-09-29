-- V36：家长手机号认证的数据约束、协议接受和首次引导状态。
-- 所有主键均由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE auth_user_agreement_acceptance (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    agreement_type VARCHAR(32) NOT NULL,
    agreement_version VARCHAR(32) NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    source_address_hash VARCHAR(64) NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_user_agreement_acceptance
        UNIQUE (user_id, agreement_type, agreement_version),
    CONSTRAINT fk_auth_user_agreement_acceptance_user
        FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_auth_user_agreement_type
        CHECK (agreement_type IN ('PARENT_USER_AGREEMENT')),
    CONSTRAINT ck_auth_user_agreement_client
        CHECK (client_type IN ('WEB', 'MINIAPP'))
);

CREATE INDEX idx_auth_user_agreement_acceptance_user_time
    ON auth_user_agreement_acceptance (user_id, accepted_at);

CREATE TABLE auth_parent_profile (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    onboarding_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    first_login_at TIMESTAMP NOT NULL,
    onboarding_completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_parent_profile_user UNIQUE (user_id),
    CONSTRAINT fk_auth_parent_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_auth_parent_profile_status
        CHECK (onboarding_status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT ck_auth_parent_profile_completion
        CHECK ((onboarding_status = 'PENDING' AND onboarding_completed_at IS NULL)
            OR (onboarding_status = 'COMPLETED' AND onboarding_completed_at IS NOT NULL))
);

INSERT INTO sys_config (
    id, config_key, config_value, value_type, is_secret, status
) VALUES
    (1874244142494646531, 'auth.parent-agreement.current-version', '1', 'STRING', 0, 'ENABLED');

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646532, 'PARENT_PHONE_AUTH', '家长手机号认证', 'GLOBAL', 'GLOBAL',
        'ENABLED', 1, '控制家长手机号验证码注册、登录和密码重置能力。');
