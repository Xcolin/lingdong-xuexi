-- V52：缓存管理 Web 闭环的功能开关和动态权限。
-- 不新增业务表；全部基础数据标识均为 19 位雪花数字。

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646587, 'CACHE_MANAGEMENT', '缓存管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制 Web 管理端的缓存操作记录、直接操作和高风险审核入口及接口。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646588, 'CACHE_READ', '查询缓存操作记录',
     'OPERATION', 'WEB', NULL, 150, 'ENABLED', '查询缓存操作历史和执行结果。'),
    (1874244142494646589, 'CACHE_MANAGE', '执行缓存管理操作',
     'OPERATION', 'WEB', NULL, 160, 'ENABLED', '执行非全量、非用户会话缓存操作，并提交高风险缓存任务。'),
    (1874244142494646590, 'CACHE_REVIEW', '审核高风险缓存任务',
     'OPERATION', 'WEB', NULL, 170, 'ENABLED', '审批或驳回系统管理员提交的高风险缓存操作任务。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646591, 1874244142494646273, 1874244142494646588, 'ALLOW'),
    (1874244142494646592, 1874244142494646273, 1874244142494646589, 'ALLOW'),
    (1874244142494646593, 1874244142494646274, 1874244142494646588, 'ALLOW'),
    (1874244142494646594, 1874244142494646274, 1874244142494646590, 'ALLOW');
