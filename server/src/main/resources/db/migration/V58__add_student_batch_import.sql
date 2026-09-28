-- V58：机构学员批量导入执行、逐行结果与一次性初始凭证交付。
-- 所有主键和内置数据标识均为19位雪花数字，禁止数据库自增。
CREATE TABLE sys_student_import_execution (
    id BIGINT NOT NULL PRIMARY KEY,
    execution_code VARCHAR(36) NOT NULL,
    validation_job_id BIGINT NOT NULL,
    requester_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    class_organization_id BIGINT,
    status VARCHAR(24) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 0,
    total_rows INT NOT NULL DEFAULT 0,
    processed_rows INT NOT NULL DEFAULT 0,
    succeeded_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    credential_file_id BIGINT,
    credential_status VARCHAR(16) NOT NULL DEFAULT 'NONE',
    credential_expires_at TIMESTAMP,
    credential_downloaded_at TIMESTAMP,
    queued_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_student_import_execution_code UNIQUE (execution_code),
    CONSTRAINT uk_sys_student_import_validation_job UNIQUE (validation_job_id),
    CONSTRAINT uk_sys_student_import_credential_file UNIQUE (credential_file_id),
    CONSTRAINT ck_sys_student_import_execution_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'PARTIAL_SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_sys_student_import_credential_status
        CHECK (credential_status IN ('NONE', 'AVAILABLE', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT ck_sys_student_import_execution_progress
        CHECK (total_rows >= 0 AND processed_rows >= 0 AND succeeded_rows >= 0
            AND failed_rows >= 0 AND processed_rows = succeeded_rows + failed_rows
            AND processed_rows <= total_rows),
    CONSTRAINT fk_sys_student_import_validation_job
        FOREIGN KEY (validation_job_id) REFERENCES sys_import_job (id),
    CONSTRAINT fk_sys_student_import_requester
        FOREIGN KEY (requester_id) REFERENCES sys_user (id),
    CONSTRAINT fk_sys_student_import_organization
        FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_sys_student_import_class
        FOREIGN KEY (class_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_sys_student_import_credential_file
        FOREIGN KEY (credential_file_id) REFERENCES sys_file (id)
);

CREATE INDEX idx_sys_student_import_status_queue
    ON sys_student_import_execution (status, id);

CREATE INDEX idx_sys_student_import_requester_created
    ON sys_student_import_execution (requester_id, created_at);

CREATE INDEX idx_sys_student_import_organization_created
    ON sys_student_import_execution (organization_id, created_at);

CREATE INDEX idx_sys_student_import_credential_expiry
    ON sys_student_import_execution (credential_status, credential_expires_at);

CREATE TABLE sys_student_import_row (
    id BIGINT NOT NULL PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    `row_number` INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    student_id BIGINT,
    student_account VARCHAR(8),
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    credential_ciphertext VARBINARY(512),
    credential_nonce VARBINARY(32),
    credential_key_version VARCHAR(32),
    attempt_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_student_import_row UNIQUE (execution_id, `row_number`),
    CONSTRAINT ck_sys_student_import_row_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_sys_student_import_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT fk_sys_student_import_row_execution
        FOREIGN KEY (execution_id) REFERENCES sys_student_import_execution (id),
    CONSTRAINT fk_sys_student_import_row_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id)
);

CREATE INDEX idx_sys_student_import_row_status
    ON sys_student_import_row (execution_id, status, `row_number`);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646644, 'STUDENT_BATCH_IMPORT', '学员批量导入',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制 Web 端机构学员批量开户、班级绑定、失败重试和一次性初始凭证交付。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646645, 'STUDENT_IMPORT_EXECUTE', '执行学员批量导入',
     'OPERATION', 'WEB', NULL, 320, 'ENABLED', '基于已通过校验的学员模板执行机构学员批量开户和班级绑定。'),
    (1874244142494646646, 'STUDENT_IMPORT_RESULT_READ', '查询学员导入结果',
     'OPERATION', 'WEB', NULL, 330, 'ENABLED', '查询本人且仍处于当前机构范围内的学员导入执行和逐行结果。'),
    (1874244142494646647, 'STUDENT_IMPORT_CREDENTIAL_DOWNLOAD', '下载学员初始凭证',
     'OPERATION', 'WEB', NULL, 340, 'ENABLED', '由原导入操作人在有效期内一次性下载学员初始凭证。');

-- 机构管理员需要先完成通用校验，再进入学员业务导入。
INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646648, 1874244142494646275, 1874244142494646626, 'ALLOW'),
    (1874244142494646649, 1874244142494646275, 1874244142494646627, 'ALLOW'),
    (1874244142494646650, 1874244142494646275, 1874244142494646645, 'ALLOW'),
    (1874244142494646651, 1874244142494646275, 1874244142494646646, 'ALLOW'),
    (1874244142494646652, 1874244142494646275, 1874244142494646647, 'ALLOW');

-- 附件内容为应用层加密包，只能经学员导入凭证服务鉴权、解密和一次性下载。
INSERT INTO sys_attachment_rule (
    id, module_code, file_category, rule_name, max_file_size_bytes,
    max_batch_count, preview_enabled, download_scope, status
) VALUES (
    1874244142494646653, 'STUDENT_IMPORT', 'INITIAL_CREDENTIAL', '学员导入初始凭证密文',
    10485760, 1, 0, 'BUSINESS_AUTHORIZED', 'ENABLED'
);

INSERT INTO sys_attachment_rule_extension (id, rule_id, extension) VALUES
    (1874244142494646654, 1874244142494646653, 'enc');
