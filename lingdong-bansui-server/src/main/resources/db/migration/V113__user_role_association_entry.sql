-- Reuse the existing canonical role-assignment permission; no new grants are needed.
UPDATE sys_menu button JOIN sys_menu page ON page.type='PAGE' AND page.route='/users'
SET button.parent_id=page.id,button.name='用户角色 · 关联角色',button.sort_order=160,button.version=button.version+1 WHERE button.code='IAM_USER_ROLE_ASSIGN' AND button.type='BUTTON';
UPDATE sys_permission permission LEFT JOIN sys_permission parent ON parent.permission_code='IAM_USER_READ'
SET permission.permission_name='用户角色 · 关联角色',permission.sort_order=160,permission.parent_id=parent.id WHERE permission.permission_code='IAM_USER_ROLE_ASSIGN';
