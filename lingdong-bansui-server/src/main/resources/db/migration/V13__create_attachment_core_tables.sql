-- Defines vendor-neutral attachment rules, file metadata, and business visibility relations.
CREATE TABLE sys_attachment_rule (
    id BIGINT NOT NULL PRIMARY KEY,
    module_code VARCHAR(64) NOT NULL,
    file_category VARCHAR(64) NOT NULL,
    rule_name VARCHAR(100) NOT NULL,
    max_file_size_bytes BIGINT NOT NULL,
    max_batch_count INT NOT NULL,
    preview_enabled TINYINT NOT NULL DEFAULT 0,
    download_scope VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_attachment_rule_module_category UNIQUE (module_code, file_category)
);

CREATE TABLE sys_attachment_rule_extension (
    id BIGINT NOT NULL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    extension VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_attachment_rule_extension UNIQUE (rule_id, extension),
    CONSTRAINT fk_sys_attachment_rule_extension_rule FOREIGN KEY (rule_id) REFERENCES sys_attachment_rule (id)
);

CREATE TABLE sys_file (
    id BIGINT NOT NULL PRIMARY KEY,
    storage_key VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    extension VARCHAR(20) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    uploaded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_file_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_sys_file_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user (id)
);

CREATE TABLE sys_file_relation (
    id BIGINT NOT NULL PRIMARY KEY,
    file_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    business_id BIGINT NOT NULL,
    relation_type VARCHAR(64) NOT NULL,
    visible_scope VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    CONSTRAINT uk_sys_file_relation_business UNIQUE (file_id, module_code, business_id, relation_type),
    CONSTRAINT fk_sys_file_relation_file FOREIGN KEY (file_id) REFERENCES sys_file (id)
);

CREATE INDEX idx_sys_attachment_rule_status ON sys_attachment_rule (status);
CREATE INDEX idx_sys_file_uploader_status ON sys_file (uploader_id, status);
CREATE INDEX idx_sys_file_relation_business_status ON sys_file_relation (module_code, business_id, status);
