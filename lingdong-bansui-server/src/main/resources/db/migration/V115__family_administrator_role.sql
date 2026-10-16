-- Preserve a pre-existing user-configured PARENT_ADMIN and its permissions.
CREATE TEMPORARY TABLE new_family_administrator_role(id BIGINT PRIMARY KEY);
INSERT INTO new_family_administrator_role(id) SELECT 1874300000000070001 WHERE NOT EXISTS(SELECT 1 FROM sys_role WHERE role_code='PARENT_ADMIN');
INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status,description)
SELECT id,'PARENT_ADMIN','家庭管理员','BUILT_IN','FAMILY',1,'ENABLED','按授权家庭限定范围，功能权限由角色授权维护' FROM new_family_administrator_role;
-- A family administrator is scoped by each explicit family role assignment, never globally.
UPDATE sys_role SET data_scope='FAMILY' WHERE role_code='PARENT_ADMIN';
-- Only seed permissions when the role did not exist before this migration.
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT 1874300000000071000+ROW_NUMBER() OVER(ORDER BY source.permission_id),target.id,source.permission_id,source.effect
FROM sys_role_permission source JOIN sys_role parent ON parent.id=source.role_id AND parent.role_code='PARENT'
JOIN new_family_administrator_role target ON 1=1;
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT 1874300000000072000+ROW_NUMBER() OVER(ORDER BY p.id),r.id,p.id,'ALLOW'
FROM new_family_administrator_role r JOIN sys_permission p ON p.permission_code IN ('ORG_NODE_READ','IAM_USER_LIST')
WHERE NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=r.id AND existing.permission_id=p.id);
DROP TEMPORARY TABLE new_family_administrator_role;
