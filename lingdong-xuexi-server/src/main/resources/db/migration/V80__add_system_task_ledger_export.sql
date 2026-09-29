-- V80：系统任务审批台账仅导出既有可见审计事实，不授予审批能力。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER', 'INTERFACE_SERVICE_LEDGER', 'CACHE_OPERATION_LOG', 'SYSTEM_TASK_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_task_scope
    CHECK (export_type <> 'SYSTEM_TASK_LEDGER'
        OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648001,'SYSTEM_TASK_EXPORT','导出系统任务审批台账','OPERATION','WEB',NULL,350,'ENABLED','复用系统任务读取权限及当前角色和领域范围生成、读取和下载本人导出文件。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648002,id,1874244142494648001,'ALLOW' FROM sys_role WHERE role_code='SYS_ADMIN';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648003,id,1874244142494648001,'ALLOW' FROM sys_role WHERE role_code='SYS_AUDITOR';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648004,r.id,p.id,'ALLOW' FROM sys_role r,sys_permission p
WHERE r.role_code='SYS_AUDITOR' AND p.permission_code='EXPORT_JOB_READ';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648005,1874244142494646611,'SYSTEM_TASK_REPORT','系统任务审批台账',80,0,'ENABLED');
