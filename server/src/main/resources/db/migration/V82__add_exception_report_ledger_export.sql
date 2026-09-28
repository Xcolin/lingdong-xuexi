-- 异常报备台账复用当前有效班级和动态教师/机构范围，不增加处理或家庭权限。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER', 'INTERFACE_SERVICE_LEDGER', 'CACHE_OPERATION_LOG', 'SYSTEM_TASK_LEDGER', 'REWARD_EXCHANGE_LEDGER', 'EXCEPTION_REPORT_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_exception_scope
    CHECK (export_type <> 'EXCEPTION_REPORT_LEDGER' OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648201,'EXCEPTION_REPORT_EXPORT','导出异常报备台账','OPERATION','WEB',NULL,370,'ENABLED','教师本人及机构授权范围内生成、读取和下载脱敏异常报备台账。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648202,id,1874244142494648201,'ALLOW' FROM sys_role WHERE role_code='TEACHER';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648203,id,1874244142494648201,'ALLOW' FROM sys_role WHERE role_code='ORG_ADMIN';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648204,r.id,p.id,'ALLOW' FROM sys_role r,sys_permission p
WHERE r.role_code='TEACHER' AND p.permission_code='EXPORT_JOB_READ';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648205,r.id,p.id,'ALLOW' FROM sys_role r,sys_permission p
WHERE r.role_code='ORG_ADMIN' AND p.permission_code='EXPORT_JOB_READ';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648206,1874244142494646611,'EXCEPTION_REPORT_EXPORT','异常报备台账',100,0,'ENABLED');
