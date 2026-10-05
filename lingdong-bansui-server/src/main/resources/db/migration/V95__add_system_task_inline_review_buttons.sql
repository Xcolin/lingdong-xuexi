-- V95: 系统任务工作台详情弹窗新增纯 UI 按钮目录（批准/驳回），支持内联审批不再跳转领域页。
-- 这些按钮仅用于显示控制，编码不动、grantable=0，不参与授权勾选与权限同步；
-- 实际审批仍由各领域接口按 @RequirePermission 与任务状态在服务端校验。
INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005120, 'system-tasks.system-task-workbench.6', '批准', 'BUTTON', 1874300000000000002, NULL, NULL, NULL, 2151, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'system-tasks.system-task-workbench.6' AND type = 'BUTTON');

INSERT INTO sys_menu (id, code, name, type, parent_id, route, icon, permission_code, sort_order, status, version)
SELECT 1874300000000005121, 'system-tasks.system-task-workbench.7', '驳回', 'BUTTON', 1874300000000000002, NULL, NULL, NULL, 2152, 'ENABLED', 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE code = 'system-tasks.system-task-workbench.7' AND type = 'BUTTON');
