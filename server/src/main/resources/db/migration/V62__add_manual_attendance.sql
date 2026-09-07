-- V62：人工考勤与不可变更正历史，所有主键和种子均为19位雪花数字。
CREATE TABLE attendance_record (
    id BIGINT NOT NULL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    class_organization_id BIGINT NOT NULL,
    attendance_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    checkin_time TIME,
    checkout_time TIME,
    source VARCHAR(16) NOT NULL DEFAULT 'MANUAL',
    recorded_by BIGINT NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_attendance_student_day UNIQUE (class_organization_id, student_id, attendance_date),
    CONSTRAINT ck_attendance_status CHECK (status IN ('NORMAL','LATE','EARLY_LEAVE','ABSENT','LEAVE')),
    CONSTRAINT ck_attendance_source CHECK (source = 'MANUAL'),
    CONSTRAINT ck_attendance_version CHECK (version_no >= 0),
    CONSTRAINT ck_attendance_times CHECK (checkin_time IS NULL OR checkout_time IS NULL OR checkin_time <= checkout_time),
    CONSTRAINT ck_attendance_absence CHECK (status NOT IN ('ABSENT','LEAVE') OR (checkin_time IS NULL AND checkout_time IS NULL)),
    CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_attendance_class FOREIGN KEY (class_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_attendance_operator FOREIGN KEY (recorded_by) REFERENCES sys_user(id)
);
CREATE INDEX idx_attendance_class_date ON attendance_record(class_organization_id, attendance_date);
CREATE INDEX idx_attendance_student_date ON attendance_record(student_id, attendance_date);
CREATE INDEX idx_attendance_status_date ON attendance_record(status, attendance_date);

CREATE TABLE attendance_record_action (
    id BIGINT NOT NULL PRIMARY KEY,
    record_id BIGINT NOT NULL,
    action_type VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    before_status VARCHAR(20),
    after_status VARCHAR(20) NOT NULL,
    before_checkin_time TIME,
    after_checkin_time TIME,
    before_checkout_time TIME,
    after_checkout_time TIME,
    version_no BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_attendance_action_version UNIQUE (record_id, version_no),
    CONSTRAINT ck_attendance_action_type CHECK (action_type IN ('CREATE','CORRECT')),
    CONSTRAINT ck_attendance_action_before CHECK (before_status IS NULL OR before_status IN ('NORMAL','LATE','EARLY_LEAVE','ABSENT','LEAVE')),
    CONSTRAINT ck_attendance_action_after CHECK (after_status IN ('NORMAL','LATE','EARLY_LEAVE','ABSENT','LEAVE')),
    CONSTRAINT fk_attendance_action_record FOREIGN KEY (record_id) REFERENCES attendance_record(id),
    CONSTRAINT fk_attendance_action_user FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
);
CREATE INDEX idx_attendance_action_record ON attendance_record_action(record_id, created_at);

INSERT INTO sys_feature_toggle(id, feature_code, feature_name, scope_type, scope_key, status, built_in, description)
VALUES (1874244142494646679, 'ATTENDANCE_MANAGEMENT', '人工考勤管理', 'GLOBAL','GLOBAL','ENABLED',1,
    '统一控制人工考勤、请假结果登记和台账查询；与地理位置能力独立。');
INSERT INTO sys_permission(id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
VALUES (1874244142494646680,'ATTENDANCE_READ','查看考勤台账','OPERATION','BOTH',NULL,450,'ENABLED','按组织、教师班级、亲子关系或本人范围查询考勤。'),
       (1874244142494646681,'ATTENDANCE_RECORD','登记考勤结果','OPERATION','BOTH',NULL,460,'ENABLED','授权人员为有效班级学生人工登记或更正考勤。');
INSERT INTO sys_role_permission(id,role_id,permission_id,effect) VALUES
    (1874244142494646682,1874244142494646275,1874244142494646680,'ALLOW'),
    (1874244142494646683,1874244142494646275,1874244142494646681,'ALLOW'),
    (1874244142494646684,1874244142494646276,1874244142494646680,'ALLOW'),
    (1874244142494646685,1874244142494646276,1874244142494646681,'ALLOW'),
    (1874244142494646686,1874244142494646277,1874244142494646680,'ALLOW'),
    (1874244142494646687,1874244142494646278,1874244142494646680,'ALLOW');
