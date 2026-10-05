-- V93: 菜单管理页新增纯 UI 按钮目录（批量按钮/移动/添加一行/提交/确定）。
-- 这些按钮仅用于显示控制，编码不动、grantable=0，不参与授权勾选与权限同步。
INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005100, 'menu-management.menu-management-page.10', '批量按钮', 'BUTTON', 1874300000000000025, NULL, NULL, NULL, 1630, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'menu-management.menu-management-page.10' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005101, 'menu-management.menu-management-page.11', '移动', 'BUTTON', 1874300000000000025, NULL, NULL, NULL, 1631, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'menu-management.menu-management-page.11' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005102, 'menu-management.menu-management-page.12', '添加一行', 'BUTTON', 1874300000000000025, NULL, NULL, NULL, 1632, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'menu-management.menu-management-page.12' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005103, 'menu-management.menu-management-page.13', '提交', 'BUTTON', 1874300000000000025, NULL, NULL, NULL, 1633, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'menu-management.menu-management-page.13' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005104, 'menu-management.menu-management-page.14', '确定', 'BUTTON', 1874300000000000025, NULL, NULL, NULL, 1634, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'menu-management.menu-management-page.14' AND type = 'BUTTON');
