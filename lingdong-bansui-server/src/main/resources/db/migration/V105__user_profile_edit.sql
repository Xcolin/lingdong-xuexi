-- Independent user-edit permission; existing user-create grants are explicitly carried forward.
INSERT INTO sys_menu(id,code,name,type,parent_id,route,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005501,'IAM_USER_UPDATE','编辑','BUTTON',id,NULL,'IAM_USER_UPDATE',1,3740,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/users';
INSERT INTO sys_menu(id,code,name,type,parent_id,route,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005502,'users.edit.save','保存','BUTTON',id,NULL,'users.edit.save',1,3750,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/users';
INSERT INTO sys_menu(id,code,name,type,parent_id,route,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005503,'users.edit.cancel','取消','BUTTON',id,NULL,'users.edit.cancel',1,3760,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/users';
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id),m.code,m.name,'BUTTON','WEB',parent.id,m.sort_order,'ENABLED','用户编辑操作权限' FROM sys_menu m
JOIN sys_menu page ON page.id=m.parent_id LEFT JOIN sys_permission parent ON parent.permission_code=page.code
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_permission) base
WHERE m.code IN('IAM_USER_UPDATE','users.edit.save','users.edit.cancel');
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY rp.role_id,p.id),rp.role_id,p.id,rp.effect FROM sys_role_permission rp
JOIN sys_permission old ON old.id=rp.permission_id AND old.permission_code='IAM_USER_CREATE'
JOIN sys_permission p ON p.permission_code IN('IAM_USER_UPDATE','users.edit.save','users.edit.cancel')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base;
INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY up.user_id,p.id),up.user_id,p.id,up.effect FROM sys_user_permission up
JOIN sys_permission old ON old.id=up.permission_id AND old.permission_code='IAM_USER_CREATE'
JOIN sys_permission p ON p.permission_code IN('IAM_USER_UPDATE','users.edit.save','users.edit.cancel')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_user_permission) base;
