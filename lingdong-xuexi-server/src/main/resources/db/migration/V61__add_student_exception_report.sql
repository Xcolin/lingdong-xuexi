-- V61：学生异常报备、不可变动作记录和本地消息事件出口。
-- 所有主键和内置数据标识均为19位雪花数字，禁止数据库自增。

CREATE TABLE edu_exception_report (
    id BIGINT NOT NULL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    class_organization_id BIGINT NOT NULL,
    reporter_user_id BIGINT NOT NULL,
    exception_type VARCHAR(24) NOT NULL,
    content VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    idempotency_key VARCHAR(64) NOT NULL,
    handled_by BIGINT,
    handled_at TIMESTAMP,
    version_no BIGINT NOT NULL DEFAULT 0,
    reported_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_exception_report_idempotency UNIQUE (reporter_user_id, idempotency_key),
    CONSTRAINT ck_exception_report_type CHECK (
        exception_type IN ('ATTENDANCE', 'LEARNING_STATUS', 'MENTAL_STATE')),
    CONSTRAINT ck_exception_report_status CHECK (status IN ('SUBMITTED', 'HANDLED')),
    CONSTRAINT ck_exception_report_handle_fields CHECK (
        (status = 'SUBMITTED' AND handled_by IS NULL AND handled_at IS NULL)
        OR (status = 'HANDLED' AND handled_by IS NOT NULL AND handled_at IS NOT NULL)),
    CONSTRAINT fk_exception_report_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_exception_report_class FOREIGN KEY (class_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_exception_report_reporter FOREIGN KEY (reporter_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_exception_report_handler FOREIGN KEY (handled_by) REFERENCES sys_user (id)
);

CREATE INDEX idx_exception_report_class_status_time
    ON edu_exception_report (class_organization_id, status, reported_at);
CREATE INDEX idx_exception_report_student_time
    ON edu_exception_report (student_id, reported_at);
CREATE INDEX idx_exception_report_reporter_time
    ON edu_exception_report (reporter_user_id, reported_at);

CREATE TABLE edu_exception_report_action (
    id BIGINT NOT NULL PRIMARY KEY,
    report_id BIGINT NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    before_status VARCHAR(16),
    after_status VARCHAR(16) NOT NULL,
    action_note VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_exception_report_action UNIQUE (report_id, action_type),
    CONSTRAINT ck_exception_report_action_type CHECK (action_type IN ('SUBMIT', 'HANDLE')),
    CONSTRAINT ck_exception_report_action_before CHECK (
        before_status IS NULL OR before_status IN ('SUBMITTED', 'HANDLED')),
    CONSTRAINT ck_exception_report_action_after CHECK (after_status IN ('SUBMITTED', 'HANDLED')),
    CONSTRAINT fk_exception_report_action_report FOREIGN KEY (report_id) REFERENCES edu_exception_report (id),
    CONSTRAINT fk_exception_report_action_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_exception_report_action_report_time
    ON edu_exception_report_action (report_id, created_at);

CREATE TABLE msg_local_event (
    id BIGINT NOT NULL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    recipient_type VARCHAR(24) NOT NULL,
    recipient_id BIGINT NOT NULL,
    content_summary VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_msg_local_event_type CHECK (
        event_type IN ('EXCEPTION_REPORT_SUBMITTED', 'EXCEPTION_REPORT_HANDLED')),
    CONSTRAINT ck_msg_local_event_recipient CHECK (
        recipient_type IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_msg_local_event_status CHECK (status IN ('PENDING', 'CONSUMED'))
);

CREATE INDEX idx_msg_local_event_status_time ON msg_local_event (status, occurred_at);
CREATE INDEX idx_msg_local_event_business ON msg_local_event (business_type, business_id);
CREATE INDEX idx_msg_local_event_recipient ON msg_local_event (recipient_type, recipient_id, occurred_at);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646671, 'STUDENT_EXCEPTION_REPORT', '学生异常报备',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制教师提交异常报备和机构管理员查看处理；停用后不生成新报备、动作或消息事件。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646672, 'EXCEPTION_REPORT_CREATE', '提交学生异常报备',
     'OPERATION', 'BOTH', NULL, 420, 'ENABLED', '教师为当前授权班级学生提交异常报备。'),
    (1874244142494646673, 'EXCEPTION_REPORT_READ', '查询学生异常报备',
     'OPERATION', 'BOTH', NULL, 430, 'ENABLED', '按教师班级或机构组织范围查询异常报备及动作历史。'),
    (1874244142494646674, 'EXCEPTION_REPORT_HANDLE', '处理学生异常报备',
     'OPERATION', 'BOTH', NULL, 440, 'ENABLED', '机构管理员在授权组织范围内标记异常报备已处理。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646675, 1874244142494646276, 1874244142494646672, 'ALLOW'),
    (1874244142494646676, 1874244142494646276, 1874244142494646673, 'ALLOW'),
    (1874244142494646677, 1874244142494646275, 1874244142494646673, 'ALLOW'),
    (1874244142494646678, 1874244142494646275, 1874244142494646674, 'ALLOW');
