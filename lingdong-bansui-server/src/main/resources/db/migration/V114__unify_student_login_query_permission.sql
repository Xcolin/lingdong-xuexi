-- Merge two codes for the same student-login query operation; DENY wins.
UPDATE sys_role_permission target JOIN sys_permission canonical ON canonical.id=target.permission_id AND canonical.permission_code='STUDENT_READ'
JOIN sys_role_permission old ON old.role_id=target.role_id JOIN sys_permission legacy ON legacy.id=old.permission_id AND legacy.permission_code='student-login.student-login-management-page.2'
SET target.effect='DENY' WHERE old.effect='DENY';
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT 1874300000000060200+ROW_NUMBER() OVER(ORDER BY old.role_id),old.role_id,canonical.id,old.effect
FROM sys_role_permission old JOIN sys_permission legacy ON legacy.id=old.permission_id AND legacy.permission_code='student-login.student-login-management-page.2'
JOIN sys_permission canonical ON canonical.permission_code='STUDENT_READ'
WHERE NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=old.role_id AND existing.permission_id=canonical.id);
UPDATE sys_user_permission target JOIN sys_permission canonical ON canonical.id=target.permission_id AND canonical.permission_code='STUDENT_READ'
JOIN sys_user_permission old ON old.user_id=target.user_id JOIN sys_permission legacy ON legacy.id=old.permission_id AND legacy.permission_code='student-login.student-login-management-page.2'
SET target.effect='DENY' WHERE old.effect='DENY';
INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT 1874300000000061200+ROW_NUMBER() OVER(ORDER BY old.user_id),old.user_id,canonical.id,old.effect
FROM sys_user_permission old JOIN sys_permission legacy ON legacy.id=old.permission_id AND legacy.permission_code='student-login.student-login-management-page.2'
JOIN sys_permission canonical ON canonical.permission_code='STUDENT_READ'
WHERE NOT EXISTS(SELECT 1 FROM sys_user_permission existing WHERE existing.user_id=old.user_id AND existing.permission_id=canonical.id);
DELETE FROM sys_role_permission WHERE permission_id IN(SELECT id FROM sys_permission WHERE permission_code='student-login.student-login-management-page.2');
DELETE FROM sys_user_permission WHERE permission_id IN(SELECT id FROM sys_permission WHERE permission_code='student-login.student-login-management-page.2');
UPDATE sys_permission child JOIN sys_permission legacy ON legacy.id=child.parent_id AND legacy.permission_code='student-login.student-login-management-page.2' JOIN sys_permission canonical ON canonical.permission_code='STUDENT_READ' SET child.parent_id=canonical.id;
DELETE FROM sys_menu WHERE type='BUTTON' AND code='student-login.student-login-management-page.2';
DELETE FROM sys_permission WHERE permission_code='student-login.student-login-management-page.2';
UPDATE sys_menu button JOIN sys_menu page ON page.type='PAGE' AND page.route='/student-login'
SET button.parent_id=page.id,button.name='学生登录凭证 · 查询学生',button.sort_order=10,button.version=button.version+1 WHERE button.code='STUDENT_READ' AND button.type='BUTTON';
UPDATE sys_permission permission JOIN sys_menu button ON button.code=permission.permission_code JOIN sys_menu page ON page.id=button.parent_id LEFT JOIN sys_permission parent ON parent.permission_code=page.permission_code
SET permission.permission_name=button.name,permission.sort_order=button.sort_order,permission.parent_id=parent.id WHERE permission.permission_code='STUDENT_READ';
