-- V77：模板配置版本台账复用异步导出，不开放模板内容或附件内部信息。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_template_scope
    CHECK (export_type <> 'TEMPLATE_LEDGER'
        OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));

INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description)
VALUES (1874244142494647701, 'IMPORT_EXPORT_TEMPLATE_EXPORT', '导出模板台账', 'OPERATION', 'WEB',
    NULL, 330, 'ENABLED', '系统管理员按当前查询和导出权限生成、下载模板配置版本台账。');
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
VALUES (1874244142494647702, 1874244142494646273, 1874244142494647701, 'ALLOW');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
VALUES (1874244142494647703, 1874244142494646611, 'TEMPLATE_REPORT', '导入导出模板台账', 50, 0, 'ENABLED');
