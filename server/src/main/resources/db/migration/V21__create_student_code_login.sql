-- V21：学生独立账号、登录码凭证及管理权限基础。
-- 所有主键由应用层雪花算法生成；迁移不生成历史学生账号或登录码。

CREATE TABLE auth_student_account_sequence (
    id BIGINT NOT NULL,
    sequence_year INT NOT NULL,
    current_value INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_student_account_sequence_year UNIQUE (sequence_year),
    CONSTRAINT ck_auth_student_account_sequence_value CHECK (current_value BETWEEN 1 AND 999999)
);

CREATE TABLE auth_student_credential (
    id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    code_hash VARCHAR(64) NOT NULL,
    code_salt VARCHAR(64) NOT NULL,
    key_version VARCHAR(32) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    captcha_required TINYINT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    code_updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_success_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_student_credential_user UNIQUE (student_user_id),
    CONSTRAINT fk_auth_student_credential_user FOREIGN KEY (student_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_auth_student_credential_failure_count CHECK (failure_count BETWEEN 0 AND 10),
    CONSTRAINT ck_auth_student_credential_captcha CHECK (captcha_required IN (0, 1))
);

CREATE INDEX idx_auth_student_credential_locked_until ON auth_student_credential (locked_until);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description
) VALUES
    (1874244142494646335, 'STUDENT_CREDENTIAL_INITIALIZE', '初始化学生登录凭证', 'OPERATION', 'WEB', NULL, 50, 'ENABLED', '主家长或学生当前机构的直接机构管理员初始化历史学生登录凭证。'),
    (1874244142494646336, 'STUDENT_LOGIN_CODE_RESET', '重置学生登录码', 'OPERATION', 'WEB', NULL, 60, 'ENABLED', '主家长或学生当前机构的直接机构管理员重置学生登录码并撤销既有会话。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646337, 1874244142494646277, 1874244142494646335),
    (1874244142494646338, 1874244142494646275, 1874244142494646335),
    (1874244142494646339, 1874244142494646277, 1874244142494646336),
    (1874244142494646340, 1874244142494646275, 1874244142494646336);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646341, 'STUDENT_CODE_LOGIN', '学生账号登录', 'GLOBAL', 'GLOBAL', 'ENABLED', 1, '控制学生账号和登录码在小程序端建立新会话的能力。');
