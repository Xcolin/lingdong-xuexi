-- V53：接口服务管理 Web 闭环的功能开关和动态权限。
-- 不新增业务表；全部基础数据标识均为 19 位雪花数字。
INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646595, 'INTERFACE_SERVICE_MANAGEMENT', '接口服务管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制接口服务登记、启停、授权范围变更、审核和台账查询。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646596, 'INTERFACE_SERVICE_READ', '查询接口服务管理台账',
     'OPERATION', 'WEB', NULL, 180, 'ENABLED', '查询接口服务、变更记录、审核队列和调用结果。'),
    (1874244142494646597, 'INTERFACE_SERVICE_MANAGE', '提交接口服务变更',
     'OPERATION', 'WEB', NULL, 190, 'ENABLED', '提交接口服务登记、启停和授权范围变更任务。'),
    (1874244142494646598, 'INTERFACE_SERVICE_REVIEW', '审核接口服务变更',
     'OPERATION', 'WEB', NULL, 200, 'ENABLED', '审批或驳回系统管理员提交的接口服务变更任务。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646599, 1874244142494646273, 1874244142494646596, 'ALLOW'),
    (1874244142494646600, 1874244142494646273, 1874244142494646597, 'ALLOW'),
    (1874244142494646601, 1874244142494646274, 1874244142494646596, 'ALLOW'),
    (1874244142494646602, 1874244142494646274, 1874244142494646598, 'ALLOW');
