-- 为统一导出执行器增加复盘 PDF 类型，仍使用原作业状态和附件关系。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_review_scope
    CHECK (export_type <> 'GROWTH_REVIEW_PDF'
        OR (student_id IS NOT NULL AND sensitive_flag = 0 AND system_task_id IS NULL));

-- 只扩展统一导出结果的格式，不覆盖管理员的大小、启停和下载范围配置。
INSERT INTO sys_attachment_rule_extension(id, rule_id, extension)
SELECT 1874244142494646689, rule.id, 'pdf' FROM sys_attachment_rule rule
WHERE rule.module_code = 'EXPORT_JOB' AND rule.file_category = 'REPORT_EXPORT'
  AND NOT EXISTS (SELECT 1 FROM sys_attachment_rule_extension ext WHERE ext.rule_id = rule.id AND ext.extension = 'pdf');
INSERT INTO sys_attachment_rule_extension(id, rule_id, extension)
SELECT 1874244142494646690, rule.id, 'zip' FROM sys_attachment_rule rule
WHERE rule.module_code = 'EXPORT_JOB' AND rule.file_category = 'REPORT_EXPORT'
  AND NOT EXISTS (SELECT 1 FROM sys_attachment_rule_extension ext WHERE ext.rule_id = rule.id AND ext.extension = 'zip');
