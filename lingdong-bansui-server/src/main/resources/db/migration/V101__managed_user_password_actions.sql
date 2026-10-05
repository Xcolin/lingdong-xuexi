-- Reuse the existing server permission as the menu button's sole code.
INSERT INTO sys_menu(id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000006100,'IAM_USER_PASSWORD_SET','重置密码','BUTTON',id,NULL,'KeyRound','IAM_USER_PASSWORD_SET',1,55,'ENABLED',1
FROM sys_menu WHERE type='PAGE' AND route='/users' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE code='IAM_USER_PASSWORD_SET');
INSERT INTO sys_menu(id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000006101,'users.reset-password.save','保存密码','BUTTON',id,NULL,'Save','users.reset-password.save',1,56,'ENABLED',1
FROM sys_menu WHERE type='PAGE' AND route='/users' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE code='users.reset-password.save');
INSERT INTO sys_menu(id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000006102,'users.reset-password.cancel','取消','BUTTON',id,NULL,NULL,'users.reset-password.cancel',1,57,'ENABLED',1
FROM sys_menu WHERE type='PAGE' AND route='/users' AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE code='users.reset-password.cancel');
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id),m.code,m.name,'BUTTON','WEB',NULL,m.sort_order,m.status,'管理用户密码操作'
FROM sys_menu m CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_permission) base
WHERE m.code IN('IAM_USER_PASSWORD_SET','users.reset-password.save','users.reset-password.cancel') AND NOT EXISTS(SELECT 1 FROM sys_permission p WHERE p.permission_code=m.code);
UPDATE sys_permission SET permission_name=(SELECT m.name FROM sys_menu m WHERE m.code=sys_permission.permission_code),resource_type='BUTTON',parent_id=(SELECT id FROM (SELECT DISTINCT p.id FROM sys_permission p JOIN sys_menu m ON m.code=p.permission_code WHERE m.route='/users') parent_permission),description='系统管理员设置或重置平台、机构和家长账号密码，并撤销旧会话。'
WHERE permission_code IN('IAM_USER_PASSWORD_SET','users.reset-password.save','users.reset-password.cancel');
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY p.id),r.id,p.id,'ALLOW'
FROM sys_permission p JOIN sys_role r ON r.role_code='SYS_ADMIN' CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE p.permission_code IN('IAM_USER_PASSWORD_SET','users.reset-password.save','users.reset-password.cancel') AND NOT EXISTS(SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
