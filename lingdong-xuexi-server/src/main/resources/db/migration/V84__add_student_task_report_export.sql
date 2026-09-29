-- 学生任务报表独立授权；不扩充既有业务查询权限。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
 CHECK (export_type IN ('GROWTH_POINT_LEDGER','IAM_CHANGE_AUDIT','GROWTH_REVIEW_PDF','DICTIONARY_LEDGER','TEMPLATE_LEDGER','INTERFACE_SERVICE_LEDGER','CACHE_OPERATION_LOG','SYSTEM_TASK_LEDGER','REWARD_EXCHANGE_LEDGER','EXCEPTION_REPORT_LEDGER','ATTACHMENT_LEDGER','STUDENT_TASK_REPORT'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_student_task_scope
 CHECK (export_type <> 'STUDENT_TASK_REPORT' OR (system_task_id IS NULL AND sensitive_flag=0));
-- 50000 个受控雪花标识约 1MB；普通 TEXT 无法容纳，MEDIUMTEXT 上限 16MiB。
ALTER TABLE sys_export_job MODIFY COLUMN scope_snapshot MEDIUMTEXT NOT NULL;
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648401,'STUDENT_TASK_REPORT_EXPORT','导出学生任务报表','OPERATION','WEB',NULL,390,'ENABLED','家长活动绑定学生，教师活动班级公开任务，机构授权组织任务；仍需对应读取权限。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648402,id,1874244142494648401,'ALLOW' FROM sys_role WHERE role_code='PARENT';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648403,id,1874244142494648401,'ALLOW' FROM sys_role WHERE role_code='TEACHER';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648404,id,1874244142494648401,'ALLOW' FROM sys_role WHERE role_code='ORG_ADMIN';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648405,1874244142494646611,'STUDENT_TASK_REPORT_EXPORT','学生任务报表',120,0,'ENABLED');
