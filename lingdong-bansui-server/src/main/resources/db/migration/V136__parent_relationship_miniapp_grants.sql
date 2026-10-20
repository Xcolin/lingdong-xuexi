-- 家长关系域放开小程序端（authorization-model.json 同步删除 6 条 policy 的 client 声明）后，
-- 内置 PARENT / PARENT_ADMIN 角色缺少这两条权限的角色关联，小程序端家长关系列表会因 findAllowedCodes
-- 只读 sys_role_permission/sys_user_permission 而不可见。为内置角色补种授权；幂等，只增不删。
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT 2201000000000112000+ROW_NUMBER() OVER(ORDER BY r.id,p.id),r.id,p.id,'ALLOW'
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('PARENT_RELATIONSHIP_READ','SECONDARY_PARENT_INVITE_CREATE') AND p.status='ENABLED'
WHERE r.role_code IN ('PARENT','PARENT_ADMIN') AND r.status='ENABLED'
  AND NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=r.id AND existing.permission_id=p.id);
