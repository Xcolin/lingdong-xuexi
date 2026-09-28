-- 沿 V54 安全元数据台账授权，不授予源文件内容权限。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER', 'INTERFACE_SERVICE_LEDGER', 'CACHE_OPERATION_LOG', 'SYSTEM_TASK_LEDGER', 'REWARD_EXCHANGE_LEDGER', 'EXCEPTION_REPORT_LEDGER', 'ATTACHMENT_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_attachment_scope
    CHECK (export_type <> 'ATTACHMENT_LEDGER' OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648301,'ATTACHMENT_FILE_LEDGER_EXPORT','导出附件管理台账','OPERATION','WEB',NULL,380,'ENABLED','仅导出已有元数据台账授权范围，不赋予源文件内容访问权限。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648302,id,1874244142494648301,'ALLOW' FROM sys_role WHERE role_code='SYS_ADMIN';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648303,1874244142494646611,'ATTACHMENT_LEDGER_REPORT','附件管理台账',110,0,'ENABLED');
