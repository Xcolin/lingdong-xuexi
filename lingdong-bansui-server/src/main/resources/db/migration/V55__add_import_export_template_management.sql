-- V55：导入导出模板管理的版本控制、统一选项、功能开关和动态权限。
-- 不新增业务表；全部基础数据标识均为 19 位雪花数字。
ALTER TABLE sys_import_export_template
    ADD COLUMN version_no BIGINT NOT NULL DEFAULT 0;

INSERT INTO sys_dictionary_type (
    id, type_code, type_name, status, sort_order
) VALUES
    (1874244142494646610, 'IMPORT_EXPORT_TEMPLATE_TYPE', '导入导出模板类型', 'ENABLED', 200),
    (1874244142494646611, 'IMPORT_EXPORT_TEMPLATE_MODULE', '导入导出模板适用模块', 'ENABLED', 210),
    (1874244142494646612, 'IMPORT_EXPORT_TEMPLATE_STATUS', '导入导出模板状态', 'ENABLED', 220);

INSERT INTO sys_dictionary_item (
    id, type_id, item_code, item_name, sort_order, is_default, status
) VALUES
    (1874244142494646613, 1874244142494646610, 'IMPORT', '导入模板', 10, 1, 'ENABLED'),
    (1874244142494646614, 1874244142494646610, 'EXPORT', '导出模板', 20, 0, 'ENABLED'),
    (1874244142494646615, 1874244142494646611, 'STUDENT', '学员', 10, 1, 'ENABLED'),
    (1874244142494646616, 1874244142494646611, 'LEARNING_TASK', '学习任务', 20, 0, 'ENABLED'),
    (1874244142494646617, 1874244142494646611, 'REPORT', '报表', 30, 0, 'ENABLED'),
    (1874244142494646618, 1874244142494646612, 'ENABLED', '启用', 10, 1, 'ENABLED'),
    (1874244142494646619, 1874244142494646612, 'DISABLED', '停用', 20, 0, 'ENABLED');

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646620, 'IMPORT_EXPORT_TEMPLATE_MANAGEMENT', '导入导出模板管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制导入导出模板配置、版本、默认项、启停和受控下载能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646621, 'IMPORT_EXPORT_TEMPLATE_READ', '查询导入导出模板',
     'OPERATION', 'WEB', NULL, 240, 'ENABLED', '查询模板配置、选项和安全文件元数据，并下载模板文件。'),
    (1874244142494646622, 'IMPORT_EXPORT_TEMPLATE_MANAGE', '管理导入导出模板',
     'OPERATION', 'WEB', NULL, 250, 'ENABLED', '上传并创建模板版本，启用、停用和设置默认模板。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646623, 1874244142494646273, 1874244142494646621, 'ALLOW'),
    (1874244142494646624, 1874244142494646273, 1874244142494646622, 'ALLOW');
