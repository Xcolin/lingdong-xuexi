-- 组织用户管理动作（成员查询/关联/移除、管理员授予/取消）在 V118 规范化后缺少内置角色授权：
-- sys_role_permission 无任何角色关联这五个权限，SYS_ADMIN/ORG_ADMIN 走 OrganizationMembershipService 全部 403。
-- 按 V17 先例补齐内置角色种子；数据范围由 policies scopeKinds 与 OrganizationMemberScopes 逐次判定。
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT 1874300000000143000 + ROW_NUMBER() OVER (ORDER BY r.role_code, p.permission_code),
       r.id, p.id, 'ALLOW'
FROM sys_role r
JOIN sys_permission p
  ON p.permission_code IN ('ORG_MEMBER_READ', 'ORG_MEMBER_ASSOCIATE', 'ORG_MEMBER_REMOVE',
                           'ORG_ADMIN_ASSIGN', 'ORG_ADMIN_REVOKE')
WHERE r.role_code IN ('SYS_ADMIN', 'ORG_ADMIN')
  AND r.status = 'ENABLED'
  AND p.status = 'ENABLED'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);
