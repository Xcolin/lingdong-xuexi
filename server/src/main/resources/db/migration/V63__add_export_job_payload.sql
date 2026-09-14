-- 导出作业内容扩展：保存复盘创建时的全文，不挤占既有筛选和范围快照。
-- 通过唯一外键关联作业；应用只提供一次写入和读取，不提供覆盖接口。
CREATE TABLE sys_export_job_payload (
    id BIGINT NOT NULL PRIMARY KEY,
    job_id BIGINT NOT NULL,
    payload_type VARCHAR(32) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_export_job_payload_job UNIQUE (job_id),
    CONSTRAINT fk_sys_export_job_payload_job FOREIGN KEY (job_id) REFERENCES sys_export_job (id),
    CONSTRAINT ck_sys_export_job_payload_id CHECK (id >= 1000000000000000000),
    CONSTRAINT ck_sys_export_job_payload_type CHECK (payload_type = 'GROWTH_REVIEW_PDF_V1')
);
