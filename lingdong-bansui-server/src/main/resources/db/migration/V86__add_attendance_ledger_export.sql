-- 考勤台账数据集；口径见 docs/design/09-统计口径与报表设计-V1.0.md 第 2.6 节。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
 CHECK (export_type IN ('GROWTH_POINT_LEDGER','IAM_CHANGE_AUDIT','GROWTH_REVIEW_PDF','DICTIONARY_LEDGER','TEMPLATE_LEDGER','INTERFACE_SERVICE_LEDGER','CACHE_OPERATION_LOG','SYSTEM_TASK_LEDGER','REWARD_EXCHANGE_LEDGER','EXCEPTION_REPORT_LEDGER','ATTACHMENT_LEDGER','STUDENT_TASK_REPORT','ORGANIZATION_TASK_STATISTICS','ATTENDANCE_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_attendance_scope
 CHECK (export_type <> 'ATTENDANCE_LEDGER' OR (system_task_id IS NULL AND sensitive_flag=0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648601,'ATTENDANCE_LEDGER_EXPORT','导出考勤台账','OPERATION','WEB',NULL,410,'ENABLED','机构管理员与教师按有效班级、家长与学生按亲子或本人范围生成、读取和下载考勤事实台账；仍需考勤读取权限。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648602,id,1874244142494648601,'ALLOW' FROM sys_role WHERE role_code='ORG_ADMIN';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648603,id,1874244142494648601,'ALLOW' FROM sys_role WHERE role_code='TEACHER';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648604,id,1874244142494648601,'ALLOW' FROM sys_role WHERE role_code='PARENT';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648605,id,1874244142494648601,'ALLOW' FROM sys_role WHERE role_code='STUDENT';
-- 学生本人导出需读取与下载自己的导出作业（沿用 V82 补授模式）。
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648607,r.id,p.id,'ALLOW' FROM sys_role r
JOIN sys_permission p ON p.permission_code='EXPORT_JOB_READ'
WHERE r.role_code='STUDENT';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648606,1874244142494646611,'ATTENDANCE_LEDGER_EXPORT','考勤台账',140,0,'ENABLED');
