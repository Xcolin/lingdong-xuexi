-- V94: 角色与权限管理页新增纯 UI 按钮目录（行内授权/批量授用户/保存授权/授予/授予角色）。
-- 这些按钮仅用于显示控制，编码不动、grantable=0，不参与授权勾选与权限同步。
INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005105, 'iam.iam-management-page.12', '授权', 'BUTTON', 1874300000000000016, NULL, NULL, NULL, 1640, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'iam.iam-management-page.12' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005106, 'iam.iam-management-page.13', '批量授用户', 'BUTTON', 1874300000000000016, NULL, NULL, NULL, 1650, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'iam.iam-management-page.13' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005107, 'iam.iam-management-page.14', '保存授权', 'BUTTON', 1874300000000000016, NULL, NULL, NULL, 1660, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'iam.iam-management-page.14' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005108, 'iam.iam-management-page.15', '授予', 'BUTTON', 1874300000000000016, NULL, NULL, NULL, 1670, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'iam.iam-management-page.15' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005109, 'iam.iam-management-page.16', '授予角色', 'BUTTON', 1874300000000000016, NULL, NULL, NULL, 1680, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'iam.iam-management-page.16' AND type = 'BUTTON');
