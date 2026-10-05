-- One management source: menu/page/button configuration. Remove abandoned UI permissions.
DELETE FROM sys_role_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code IN ('IAM_PERMISSION_CREATE','iam.iam-management-page.2','iam.iam-management-page.8','iam.iam-management-page.9','organizations.node.members'));
DELETE FROM sys_user_permission WHERE permission_id IN (SELECT id FROM sys_permission WHERE permission_code IN ('IAM_PERMISSION_CREATE','iam.iam-management-page.2','iam.iam-management-page.8','iam.iam-management-page.9','organizations.node.members'));
DELETE FROM sys_menu WHERE code IN ('IAM_PERMISSION_CREATE','iam.iam-management-page.2','iam.iam-management-page.8','iam.iam-management-page.9','organizations.node.members');
DELETE FROM sys_permission WHERE permission_code IN ('IAM_PERMISSION_CREATE','iam.iam-management-page.2','iam.iam-management-page.8','iam.iam-management-page.9','organizations.node.members');

INSERT INTO sys_menu(id,code,name,type,parent_id,route,icon,permission_code,grantable,sort_order,status,version) VALUES
(1874300000000006000,'permission-management','权限管理','DIRECTORY',NULL,NULL,'ShieldCheck',NULL,0,20,'ENABLED',1),
(1874300000000006001,'system-settings','系统设置管理','DIRECTORY',NULL,NULL,'Gauge',NULL,0,30,'ENABLED',1),
(1874300000000006002,'import-export-management','导入导出管理','DIRECTORY',NULL,NULL,'Files',NULL,0,40,'ENABLED',1),
(1874300000000006003,'system-operations','系统运维','DIRECTORY',NULL,NULL,'Cable',NULL,0,50,'ENABLED',1),
(1874300000000006004,'education-management','教育管理','DIRECTORY',NULL,NULL,'GraduationCap',NULL,0,60,'ENABLED',1),
(1874300000000006005,'growth-management','成长管理','DIRECTORY',NULL,NULL,'Gift',NULL,0,70,'ENABLED',1);

UPDATE sys_menu SET parent_id=1874300000000006000,sort_order=CASE route WHEN '/organizations' THEN 10 WHEN '/users' THEN 20 WHEN '/iam' THEN 30 ELSE 40 END,version=version+1 WHERE type='PAGE' AND route IN('/organizations','/users','/iam','/menu-management');
UPDATE sys_menu SET parent_id=1874300000000006001,sort_order=CASE route WHEN '/dictionaries' THEN 10 WHEN '/cache-management' THEN 20 ELSE 30 END,version=version+1 WHERE type='PAGE' AND route IN('/dictionaries','/cache-management','/feature-management');
UPDATE sys_menu SET parent_id=1874300000000006002,sort_order=CASE route WHEN '/import-export-templates' THEN 10 WHEN '/import-jobs' THEN 20 ELSE 30 END,version=version+1 WHERE type='PAGE' AND route IN('/import-export-templates','/import-jobs','/export-jobs');
UPDATE sys_menu SET parent_id=1874300000000006003,sort_order=CASE route WHEN '/system-tasks' THEN 10 WHEN '/interface-services' THEN 20 ELSE 30 END,version=version+1 WHERE type='PAGE' AND route IN('/system-tasks','/interface-services','/attachment-management');
UPDATE sys_menu SET parent_id=1874300000000006004,version=version+1 WHERE type='PAGE' AND route IN('/attendance-records','/learning-tasks','/exception-reports','/teachers','/student-login','/parent-relationships');
UPDATE sys_menu SET parent_id=1874300000000006005,version=version+1 WHERE type='PAGE' AND route IN('/growth-points','/rewards','/growth-reviews','/anonymous-ranks','/rank-preferences');

UPDATE sys_permission SET permission_name=(SELECT m.name FROM sys_menu m WHERE m.code=sys_permission.permission_code),resource_type=(SELECT m.type FROM sys_menu m WHERE m.code=sys_permission.permission_code),sort_order=(SELECT m.sort_order FROM sys_menu m WHERE m.code=sys_permission.permission_code),status=(SELECT m.status FROM sys_menu m WHERE m.code=sys_permission.permission_code) WHERE EXISTS(SELECT 1 FROM sys_menu m WHERE m.code=sys_permission.permission_code AND m.type IN('PAGE','BUTTON'));
UPDATE sys_permission SET parent_id=(SELECT parent_permission_id FROM (SELECT DISTINCT p.id AS child_permission_id,pp.id AS parent_permission_id FROM sys_permission p JOIN sys_menu m ON m.code=p.permission_code LEFT JOIN sys_menu pm ON pm.id=m.parent_id LEFT JOIN sys_permission pp ON pp.permission_code=pm.permission_code WHERE m.type IN('PAGE','BUTTON')) hierarchy WHERE hierarchy.child_permission_id=sys_permission.id) WHERE EXISTS(SELECT 1 FROM sys_menu m WHERE m.code=sys_permission.permission_code AND m.type IN('PAGE','BUTTON'));
