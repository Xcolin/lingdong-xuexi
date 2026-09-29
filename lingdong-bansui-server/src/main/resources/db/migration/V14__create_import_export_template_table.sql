-- 保存导入导出模板的版本化元数据，文件内容仍由附件模块统一管理。
CREATE TABLE sys_import_export_template (
    id BIGINT NOT NULL PRIMARY KEY,
    template_name VARCHAR(100) NOT NULL,
    template_type VARCHAR(16) NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    file_id BIGINT NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    default_scope_key VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_template_module_type_version UNIQUE (module_code, template_type, version),
    CONSTRAINT uk_sys_template_default_scope UNIQUE (module_code, template_type, default_scope_key),
    CONSTRAINT fk_sys_template_file FOREIGN KEY (file_id) REFERENCES sys_file (id)
);

CREATE INDEX idx_sys_template_module_type_status
    ON sys_import_export_template (module_code, template_type, status);
