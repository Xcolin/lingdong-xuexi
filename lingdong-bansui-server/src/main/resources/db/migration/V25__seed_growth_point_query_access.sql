-- V25：积分账户与台账安全查询开关及最小角色权限。

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646375, 'GROWTH_POINT_QUERY', '积分账户与台账查询',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制学生本人和主家长的积分账户及不可变台账查询能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646376, 'GROWTH_POINT_READ_SELF', '查询本人积分账户与台账',
        'OPERATION', 'MINIAPP', NULL, 160, 'ENABLED',
        '学生在小程序查询本人积分账户与台账。'),
    (1874244142494646377, 'GROWTH_POINT_READ_CHILD', '查询孩子积分账户与台账',
        'OPERATION', 'WEB', NULL, 170, 'ENABLED',
        '主家长在 Web 查询活动主关系学生的积分账户与台账。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646378, 1874244142494646278, 1874244142494646376),
    (1874244142494646379, 1874244142494646277, 1874244142494646377);
