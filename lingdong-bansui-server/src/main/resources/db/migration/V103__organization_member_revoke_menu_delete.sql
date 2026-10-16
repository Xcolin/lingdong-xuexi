-- 组织用户撤销操作、菜单节点删除 与 审计事件类型扩展。
-- 1) 组织管理：成员「取消关联」、组织管理员「取消管理员」（与 organizations.members.add/add-admin/promote 同族，父级=组织管理页面）。
-- 2) 菜单管理：节点「删除」按钮（与 menu-management.node.add-child 同族，父级=菜单管理页面）。
-- 3) 审计事件类型扩展：MENU_DELETE、USER_ORGANIZATION_DISSOCIATE、ORGANIZATION_ADMIN_REVOKE、USER_ROLE_REMOVE。
-- 授权口径：SYS_ADMIN 授全部三个新按钮；ORG_ADMIN 授组织用户撤销两个按钮（机构管理员管理本组织成员所需）。

INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005410,'organizations.members.dissociate','取消关联','BUTTON',id,NULL,NULL,'organizations.members.dissociate',1,3700,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='organizations.members.dissociate');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005411,'organizations.members.revoke-admin','取消管理员','BUTTON',id,NULL,NULL,'organizations.members.revoke-admin',1,3710,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='organizations.members.revoke-admin');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005412,'menu-management.node.delete','删除','BUTTON',id,NULL,NULL,'menu-management.node.delete',1,3720,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/menu-management' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='menu-management.node.delete');

INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id),m.code,m.name,'BUTTON','WEB',NULL,m.sort_order,m.status,'菜单按钮独立授权权限'
FROM sys_menu m CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_permission) base
WHERE m.type='BUTTON' AND m.code IN ('organizations.members.dissociate','organizations.members.revoke-admin','menu-management.node.delete')
  AND NOT EXISTS(SELECT 1 FROM sys_permission p WHERE p.permission_code=m.code);

-- 按钮权限挂到所属页面权限之下（与 V100 权限父级回填同款派生表写法，MySQL 与 H2 兼容）。
UPDATE sys_permission SET parent_id=(SELECT hierarchy.parent_permission_id FROM (
  SELECT DISTINCT m.code AS button_code, pp.id AS parent_permission_id
  FROM sys_menu m JOIN sys_menu pm ON pm.id=m.parent_id JOIN sys_permission pp ON pp.permission_code=pm.code
  WHERE m.type='BUTTON' AND m.code IN ('organizations.members.dissociate','organizations.members.revoke-admin','menu-management.node.delete')
) hierarchy WHERE hierarchy.button_code=sys_permission.permission_code)
WHERE EXISTS(SELECT 1 FROM sys_menu m WHERE m.code=sys_permission.permission_code AND m.type='BUTTON' AND m.code IN ('organizations.members.dissociate','organizations.members.revoke-admin','menu-management.node.delete'));

INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id,r.id),r.id,p.id,'ALLOW'
FROM sys_menu m JOIN sys_permission p ON p.permission_code=m.code JOIN sys_role r ON r.role_code='SYS_ADMIN'
CROSS JOIN(SELECT COALESCE(MAX(id),0) AS max_id FROM sys_role_permission) base
WHERE m.type='BUTTON' AND m.code IN ('organizations.members.dissociate','organizations.members.revoke-admin','menu-management.node.delete')
  AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id,r.id),r.id,p.id,'ALLOW'
FROM sys_menu m JOIN sys_permission p ON p.permission_code=m.code JOIN sys_role r ON r.role_code='ORG_ADMIN'
CROSS JOIN(SELECT COALESCE(MAX(id),0) AS max_id FROM sys_role_permission) base
WHERE m.type='BUTTON' AND m.code IN ('organizations.members.dissociate','organizations.members.revoke-admin')
  AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);

ALTER TABLE sys_iam_change_audit DROP CONSTRAINT ck_sys_iam_change_audit_event;
ALTER TABLE sys_iam_change_audit ADD CONSTRAINT ck_sys_iam_change_audit_event CHECK (event_type IN (
 'USER_CREATE','USER_PROFILE_CHANGE','USER_PASSWORD_RESET','USER_STATUS_CHANGE','USER_ORGANIZATION_ASSOCIATE','USER_ROLE_ASSIGN','ROLE_CREATE','PERMISSION_CREATE','ROLE_PERMISSION_CONFIGURE','ROLE_PERMISSION_REMOVE','USER_PERMISSION_CONFIGURE','USER_PERMISSION_REMOVE','ROLE_DATA_SCOPE_ADD','ORGANIZATION_ADMIN_ASSIGN','MENU_CREATE','MENU_UPDATE','MENU_REORDER','ORGANIZATION_TREE_REORDER','ORGANIZATION_TREE_MOVE','MENU_DELETE','USER_ORGANIZATION_DISSOCIATE','ORGANIZATION_ADMIN_REVOKE','USER_ROLE_REMOVE'
));
