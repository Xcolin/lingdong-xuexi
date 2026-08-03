-- V17：组织类型与组织树管理接口的 Web 操作权限。
-- 本迁移只写入既有 RBAC 表的基础数据，所有标识均为预生成的 19 位雪花常量。

INSERT INTO sys_permission (
    id,
    permission_code,
    permission_name,
    resource_type,
    client_type,
    parent_id,
    sort_order,
    status,
    description
) VALUES
    (1874244142494646312, 'ORG_TYPE_READ', '查询组织类型', 'OPERATION', 'WEB', NULL, 10, 'ENABLED', '查询组织类型目录。'),
    (1874244142494646313, 'ORG_TYPE_CREATE', '创建组织类型', 'OPERATION', 'WEB', NULL, 20, 'ENABLED', '创建自定义组织类型。'),
    (1874244142494646314, 'ORG_NODE_READ', '查询组织树', 'OPERATION', 'WEB', NULL, 30, 'ENABLED', '查询区域、学校等组织树。'),
    (1874244142494646315, 'ORG_NODE_CREATE', '创建组织节点', 'OPERATION', 'WEB', NULL, 40, 'ENABLED', '创建区域、学校等组织节点。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646316, 1874244142494646273, 1874244142494646312),
    (1874244142494646317, 1874244142494646273, 1874244142494646313),
    (1874244142494646318, 1874244142494646273, 1874244142494646314),
    (1874244142494646319, 1874244142494646273, 1874244142494646315);
