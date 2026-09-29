-- V35：学生扫码登录一次性票据、功能开关与签发权限。
-- 所有主键均由应用层雪花算法生成，迁移不使用数据库自增列。

CREATE TABLE auth_student_qr_ticket (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP NULL,
    issued_by_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_student_qr_ticket_token UNIQUE (token_hash),
    CONSTRAINT fk_auth_student_qr_ticket_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_auth_student_qr_ticket_student_user
        FOREIGN KEY (student_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_auth_student_qr_ticket_issuer
        FOREIGN KEY (issued_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_auth_student_qr_ticket_status
        CHECK (status IN ('ACTIVE', 'CONSUMED', 'REVOKED')),
    CONSTRAINT ck_auth_student_qr_ticket_consumed
        CHECK ((status = 'CONSUMED' AND consumed_at IS NOT NULL)
            OR (status <> 'CONSUMED' AND consumed_at IS NULL))
);

CREATE INDEX idx_auth_student_qr_ticket_student_status
    ON auth_student_qr_ticket (student_id, status, expires_at);
CREATE INDEX idx_auth_student_qr_ticket_expiry
    ON auth_student_qr_ticket (status, expires_at);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646527, 'STUDENT_QR_LOGIN', '学生扫码登录', 'GLOBAL', 'GLOBAL',
        'ENABLED', 1, '控制 Web 端学生登录二维码签发和小程序端扫码登录能力。');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646528, 'STUDENT_LOGIN_QR_CREATE', '生成学生登录二维码',
        'OPERATION', 'WEB', NULL, 70, 'ENABLED',
        '主家长或学生当前机构的直接机构管理员生成一次性学生登录二维码。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646529, 1874244142494646277, 1874244142494646528),
    (1874244142494646530, 1874244142494646275, 1874244142494646528);
