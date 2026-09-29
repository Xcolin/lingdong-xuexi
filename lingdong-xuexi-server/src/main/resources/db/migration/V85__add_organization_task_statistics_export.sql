-- 机构任务统计数据集独立授权；口径见 docs/design/09-统计口径与报表设计-V1.0.md 第 2.5 节。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
 CHECK (export_type IN ('GROWTH_POINT_LEDGER','IAM_CHANGE_AUDIT','GROWTH_REVIEW_PDF','DICTIONARY_LEDGER','TEMPLATE_LEDGER','INTERFACE_SERVICE_LEDGER','CACHE_OPERATION_LOG','SYSTEM_TASK_LEDGER','REWARD_EXCHANGE_LEDGER','EXCEPTION_REPORT_LEDGER','ATTACHMENT_LEDGER','STUDENT_TASK_REPORT','ORGANIZATION_TASK_STATISTICS'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_org_stat_scope
 CHECK (export_type <> 'ORGANIZATION_TASK_STATISTICS' OR (system_task_id IS NULL AND sensitive_flag=0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648501,'ORGANIZATION_TASK_STATISTICS_EXPORT','导出机构任务统计','OPERATION','WEB',NULL,400,'ENABLED','机构管理员导出授权组织树内按来源班级聚合的任务统计；仍需对应读取权限，家庭与教师来源排除。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648502,id,1874244142494648501,'ALLOW' FROM sys_role WHERE role_code='ORG_ADMIN';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648503,1874244142494646611,'ORGANIZATION_TASK_STATISTICS_EXPORT','机构任务统计',130,0,'ENABLED');
