-- V56：通用 XLSX 导入校验作业基础能力。
-- 校验作业不写入业务事实；所有主键由应用层生成 19 位雪花数字。
CREATE TABLE sys_import_export_template_field (
    id BIGINT NOT NULL PRIMARY KEY,
    template_id BIGINT NOT NULL,
    field_code VARCHAR(64) NOT NULL,
    column_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(16) NOT NULL,
    required_flag TINYINT NOT NULL DEFAULT 0,
    max_length INT,
    dictionary_type_code VARCHAR(64),
    sort_order INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_import_template_field_code UNIQUE (template_id, field_code),
    CONSTRAINT uk_sys_import_template_column_name UNIQUE (template_id, column_name),
    CONSTRAINT uk_sys_import_template_field_order UNIQUE (template_id, sort_order),
    CONSTRAINT fk_sys_import_template_field_template
        FOREIGN KEY (template_id) REFERENCES sys_import_export_template (id)
);

CREATE INDEX idx_sys_import_template_field_template
    ON sys_import_export_template_field (template_id, sort_order);

CREATE TABLE sys_import_job (
    id BIGINT NOT NULL PRIMARY KEY,
    job_code VARCHAR(36) NOT NULL,
    template_id BIGINT NOT NULL,
    template_version VARCHAR(32) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    field_mapping_snapshot TEXT NOT NULL,
    source_file_id BIGINT NOT NULL,
    error_file_id BIGINT,
    requester_id BIGINT NOT NULL,
    organization_id BIGINT,
    status VARCHAR(24) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    queued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_import_job_code UNIQUE (job_code),
    CONSTRAINT fk_sys_import_job_template
        FOREIGN KEY (template_id) REFERENCES sys_import_export_template (id),
    CONSTRAINT fk_sys_import_job_source_file
        FOREIGN KEY (source_file_id) REFERENCES sys_file (id),
    CONSTRAINT fk_sys_import_job_error_file
        FOREIGN KEY (error_file_id) REFERENCES sys_file (id),
    CONSTRAINT fk_sys_import_job_requester
        FOREIGN KEY (requester_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_import_job_organization
        FOREIGN KEY (organization_id) REFERENCES sys_organization (id)
);

CREATE INDEX idx_sys_import_job_status_queue
    ON sys_import_job (status, id);

CREATE INDEX idx_sys_import_job_requester_created
    ON sys_import_job (requester_id, created_at);

CREATE INDEX idx_sys_import_job_organization_created
    ON sys_import_job (organization_id, created_at);

CREATE TABLE sys_import_job_row_result (
    id BIGINT NOT NULL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    `row_number` INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_summary VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_import_job_row UNIQUE (job_id, `row_number`),
    CONSTRAINT fk_sys_import_job_row_job
        FOREIGN KEY (job_id) REFERENCES sys_import_job (id)
);

CREATE INDEX idx_sys_import_job_row_status
    ON sys_import_job_row_result (job_id, status, `row_number`);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646625, 'DATA_IMPORT_VALIDATION', '数据导入校验',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制 Web 端 XLSX 导入模板字段映射、异步校验、结果台账和错误文件能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646626, 'IMPORT_JOB_READ', '查询导入校验作业',
     'OPERATION', 'WEB', NULL, 260, 'ENABLED', '查询授权范围内的导入校验作业、逐行错误和错误文件。'),
    (1874244142494646627, 'IMPORT_JOB_CREATE', '创建导入校验作业',
     'OPERATION', 'WEB', NULL, 270, 'ENABLED', '上传受控 XLSX 文件并创建只执行校验的异步作业。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646628, 1874244142494646273, 1874244142494646626, 'ALLOW'),
    (1874244142494646629, 1874244142494646273, 1874244142494646627, 'ALLOW');

-- 空库部署后即可上传受控 XLSX；下载仍由导入作业对象级权限校验。
INSERT INTO sys_attachment_rule (
    id, module_code, file_category, rule_name, max_file_size_bytes,
    max_batch_count, preview_enabled, download_scope, status
) VALUES (
    1874244142494646630, 'IMPORT_JOB', 'IMPORT_VALIDATION', '导入校验文件',
    10485760, 1, 0, 'BUSINESS_AUTHORIZED', 'ENABLED'
);

INSERT INTO sys_attachment_rule_extension (id, rule_id, extension) VALUES
    (1874244142494646631, 1874244142494646630, 'xlsx');
