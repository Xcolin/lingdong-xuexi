-- V46：学生微信授权当前绑定、不可变审计、功能开关和主监护人解绑权限。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

CREATE TABLE auth_student_wechat_binding (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    open_id VARCHAR(128) NOT NULL,
    union_id VARCHAR(128) NULL,
    bound_at TIMESTAMP NOT NULL,
    last_login_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_auth_student_wechat_binding_student UNIQUE (student_id),
    CONSTRAINT uk_auth_student_wechat_binding_user UNIQUE (student_user_id),
    CONSTRAINT uk_auth_student_wechat_binding_wechat UNIQUE (app_id, open_id),
    CONSTRAINT fk_auth_student_wechat_binding_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_auth_student_wechat_binding_user
        FOREIGN KEY (student_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_auth_student_wechat_binding_union
    ON auth_student_wechat_binding (union_id);

CREATE TABLE auth_student_wechat_binding_audit (
    id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    client_type VARCHAR(16) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_auth_student_wechat_audit_event
        CHECK (event_type IN ('BIND', 'UNBIND')),
    CONSTRAINT ck_auth_student_wechat_audit_client
        CHECK (client_type IN ('WEB', 'MINIAPP')),
    CONSTRAINT fk_auth_student_wechat_audit_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_auth_student_wechat_audit_user
        FOREIGN KEY (student_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_auth_student_wechat_audit_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_auth_student_wechat_audit_student_time
    ON auth_student_wechat_binding_audit (student_id, occurred_at, id);

CREATE INDEX idx_auth_student_wechat_audit_operator_time
    ON auth_student_wechat_binding_audit (operator_user_id, occurred_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646561, 'STUDENT_WECHAT_AUTH', '学生微信授权登录',
    'GLOBAL', 'GLOBAL', 'DISABLED', 1,
    '控制学生微信小程序授权、自助双重绑定、快捷登录和主监护人解绑能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646562, 'STUDENT_WECHAT_UNBIND', '解绑学生微信',
    'OPERATION', 'BOTH', NULL, 79, 'ENABLED',
    '当前主监护人在家长侧查看学生微信绑定状态并执行二次确认解绑。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646563, 1874244142494646277, 1874244142494646562);
