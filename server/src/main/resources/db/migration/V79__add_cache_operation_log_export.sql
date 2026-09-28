-- V79：缓存操作日志复用异步导出，不开放密钥、地址或报文。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER', 'INTERFACE_SERVICE_LEDGER', 'CACHE_OPERATION_LOG'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_cache_scope
    CHECK (export_type <> 'CACHE_OPERATION_LOG'
        OR (student_id IS NULL AND system_task_id IS NULL AND sensitive_flag = 0));

INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description)
VALUES (1874244142494647901, 'CACHE_EXPORT', '导出缓存操作日志', 'OPERATION', 'WEB',
    NULL, 340, 'ENABLED', '系统管理员按当前查询和导出权限生成、下载缓存操作日志。');
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
VALUES (1874244142494647902, 1874244142494646273, 1874244142494647901, 'ALLOW');
INSERT INTO sys_dictionary_item (id, type_id, item_code, item_name, sort_order, is_default, status)
VALUES (1874244142494647903, 1874244142494646611, 'CACHE_REPORT', '缓存操作日志', 70, 0, 'ENABLED');
