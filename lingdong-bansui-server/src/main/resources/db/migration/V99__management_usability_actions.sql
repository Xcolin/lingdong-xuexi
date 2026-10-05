-- Independent actions for management page usability.
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005400,'users.directory.reset','重置','BUTTON',id,NULL,NULL,'users.directory.reset',1,3650,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/users' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='users.directory.reset');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005401,'iam.directory.query','查询','BUTTON',id,NULL,NULL,'iam.directory.query',1,3660,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/iam' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='iam.directory.query');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005402,'iam.directory.reset','重置','BUTTON',id,NULL,NULL,'iam.directory.reset',1,3670,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/iam' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='iam.directory.reset');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005403,'organizations.node.add-child','新增下级','BUTTON',id,NULL,NULL,'organizations.node.add-child',1,3680,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='organizations.node.add-child');
INSERT INTO sys_menu (id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) SELECT 1874300000000005404,'menu-management.node.add-child','新增下级','BUTTON',id,NULL,NULL,'menu-management.node.add-child',1,3690,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/menu-management' AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE code='menu-management.node.add-child');
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id),m.code,m.name,'BUTTON','WEB',NULL,m.sort_order,m.status,'菜单按钮独立授权权限'
FROM sys_menu m CROSS JOIN (SELECT COALESCE(MAX(id),0) AS max_id FROM sys_permission) base
WHERE m.type='BUTTON' AND NOT EXISTS(SELECT 1 FROM sys_permission p WHERE p.permission_code=m.code);
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id,r.id),r.id,p.id,'ALLOW'
FROM sys_menu m JOIN sys_permission p ON p.permission_code=m.code JOIN sys_role r ON r.role_code='SYS_ADMIN'
CROSS JOIN(SELECT COALESCE(MAX(id),0) AS max_id FROM sys_role_permission) base
WHERE m.type='BUTTON' AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
