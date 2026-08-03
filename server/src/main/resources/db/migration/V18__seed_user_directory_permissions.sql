-- V18：用户目录查询与账号状态管理的 Web 操作权限。
-- 安全：仅增加 RBAC 基础数据，不修改账号资料、密码散列或设备会话。

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description
) VALUES
    (1874244142494646320, 'IAM_USER_LIST', '查询用户目录', 'OPERATION', 'WEB', NULL, 10, 'ENABLED', '按条件分页查询用户安全资料。'),
    (1874244142494646321, 'IAM_USER_STATUS_CHANGE', '变更用户状态', 'OPERATION', 'WEB', NULL, 20, 'ENABLED', '启用、停用或锁定用户账号。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646322, 1874244142494646273, 1874244142494646320),
    (1874244142494646323, 1874244142494646273, 1874244142494646321);
