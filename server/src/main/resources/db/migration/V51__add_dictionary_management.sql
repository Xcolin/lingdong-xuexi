-- V51：数据字典 Web 管理闭环的功能开关和动态权限。
-- 不新增业务表；全部基础数据标识均为 19 位雪花数字。

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646582, 'DICTIONARY_MANAGEMENT', '数据字典管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制 Web 管理端的数据字典类型与字典项维护入口和接口。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646583, 'DICTIONARY_READ', '查询数据字典',
     'OPERATION', 'WEB', NULL, 130, 'ENABLED', '查询数据字典类型和包含停用历史项的字典项目录。'),
    (1874244142494646584, 'DICTIONARY_MANAGE', '维护数据字典',
     'OPERATION', 'WEB', NULL, 140, 'ENABLED', '新增或修改普通字典类型和字典项。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646585, 1874244142494646273, 1874244142494646583, 'ALLOW'),
    (1874244142494646586, 1874244142494646273, 1874244142494646584, 'ALLOW');

