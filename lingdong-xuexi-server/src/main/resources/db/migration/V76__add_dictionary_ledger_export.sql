-- V76：数据字典台账复用异步导出链路，独立配置模板和最小操作权限。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_dictionary_scope
    CHECK (export_type <> 'DICTIONARY_LEDGER'
        OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (1874244142494647601, 'DICTIONARY_EXPORT', '导出数据字典台账',
    'OPERATION', 'WEB', NULL, 320, 'ENABLED', '系统管理员按当前字典查询和导出权限生成、下载字典台账。');
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
VALUES (1874244142494647602, 1874244142494646273, 1874244142494647601, 'ALLOW');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
VALUES (1874244142494647603, 1874244142494646611, 'DICTIONARY_REPORT', '数据字典台账', 40, 0, 'ENABLED');
