-- V57：通用异步 Excel 导出作业、不可变状态事件、功能开关和最小权限。
-- 导出文件由统一附件服务管理；所有主键和基础数据标识均为 19 位雪花数字。
CREATE TABLE sys_export_job (
    id BIGINT NOT NULL PRIMARY KEY,
    job_code VARCHAR(32) NOT NULL,
    export_type VARCHAR(32) NOT NULL,
    template_id BIGINT NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    requester_id BIGINT NOT NULL,
    student_id BIGINT,
    system_task_id BIGINT,
    filter_snapshot TEXT NOT NULL,
    column_snapshot TEXT NOT NULL,
    scope_snapshot TEXT NOT NULL,
    mask_policy_snapshot TEXT NOT NULL,
    request_reason VARCHAR(500) NOT NULL,
    sensitive_flag TINYINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    result_file_id BIGINT,
    total_rows BIGINT NOT NULL DEFAULT 0,
    processed_rows BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    request_source_hash VARCHAR(64) NOT NULL,
    requested_at TIMESTAMP NOT NULL,
    reviewed_at TIMESTAMP,
    queued_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_export_job_code UNIQUE (job_code),
    CONSTRAINT uk_sys_export_job_system_task UNIQUE (system_task_id),
    CONSTRAINT uk_sys_export_job_result_file UNIQUE (result_file_id),
    CONSTRAINT ck_sys_export_job_type
        CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT')),
    CONSTRAINT ck_sys_export_job_status
        CHECK (status IN ('PENDING_REVIEW', 'QUEUED', 'EXPORTING', 'SUCCEEDED', 'FAILED', 'REJECTED')),
    CONSTRAINT ck_sys_export_job_sensitive CHECK (sensitive_flag IN (0, 1)),
    CONSTRAINT ck_sys_export_job_progress
        CHECK (total_rows >= 0 AND processed_rows >= 0 AND processed_rows <= total_rows),
    CONSTRAINT fk_sys_export_job_template
        FOREIGN KEY (template_id) REFERENCES sys_import_export_template (id),
    CONSTRAINT fk_sys_export_job_requester
        FOREIGN KEY (requester_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_export_job_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_sys_export_job_system_task
        FOREIGN KEY (system_task_id) REFERENCES sys_system_task (id),
    CONSTRAINT fk_sys_export_job_result_file
        FOREIGN KEY (result_file_id) REFERENCES sys_file (id)
);

CREATE INDEX idx_sys_export_job_status_queue
    ON sys_export_job (status, id);

CREATE INDEX idx_sys_export_job_requester_created
    ON sys_export_job (requester_id, created_at);

CREATE INDEX idx_sys_export_job_type_created
    ON sys_export_job (export_type, created_at);

CREATE TABLE sys_export_job_event (
    id BIGINT NOT NULL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    operator_id BIGINT,
    summary VARCHAR(500) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sys_export_job_event_type
        CHECK (event_type IN ('REQUESTED', 'REVIEW_SUBMITTED', 'APPROVED', 'REJECTED',
            'CLAIMED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT fk_sys_export_job_event_job
        FOREIGN KEY (job_id) REFERENCES sys_export_job (id),
    CONSTRAINT fk_sys_export_job_event_operator
        FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_export_job_event_job_time
    ON sys_export_job_event (job_id, occurred_at, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646632, 'DATA_EXPORT', '数据导出', 'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制 Web 端异步 Excel 导出、敏感导出审批、导出台账和结果下载能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646633, 'EXPORT_JOB_READ', '查询本人导出作业',
     'OPERATION', 'WEB', NULL, 280, 'ENABLED', '查询本人异步导出作业并按当前对象权限下载结果。'),
    (1874244142494646634, 'EXPORT_JOB_CREATE', '创建普通导出作业',
     'OPERATION', 'WEB', NULL, 290, 'ENABLED', '在业务对象范围内创建普通异步导出作业。'),
    (1874244142494646635, 'EXPORT_SENSITIVE_SUBMIT', '提交敏感导出申请',
     'OPERATION', 'WEB', NULL, 300, 'ENABLED', '系统管理员提交需要系统审核员审批的敏感导出申请。'),
    (1874244142494646636, 'EXPORT_SENSITIVE_REVIEW', '审核敏感导出申请',
     'OPERATION', 'WEB', NULL, 310, 'ENABLED', '系统审核员仅查看脱敏申请元数据并批准或驳回敏感导出。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646637, 1874244142494646277, 1874244142494646633, 'ALLOW'),
    (1874244142494646638, 1874244142494646277, 1874244142494646634, 'ALLOW'),
    (1874244142494646639, 1874244142494646273, 1874244142494646633, 'ALLOW'),
    (1874244142494646640, 1874244142494646273, 1874244142494646635, 'ALLOW'),
    (1874244142494646641, 1874244142494646274, 1874244142494646636, 'ALLOW');

INSERT INTO sys_attachment_rule (
    id, module_code, file_category, rule_name, max_file_size_bytes,
    max_batch_count, preview_enabled, download_scope, status
) VALUES (
    1874244142494646642, 'EXPORT_JOB', 'REPORT_EXPORT', '异步导出结果文件',
    52428800, 1, 0, 'BUSINESS_AUTHORIZED', 'ENABLED'
);

INSERT INTO sys_attachment_rule_extension (id, rule_id, extension) VALUES
    (1874244142494646643, 1874244142494646642, 'xlsx');
