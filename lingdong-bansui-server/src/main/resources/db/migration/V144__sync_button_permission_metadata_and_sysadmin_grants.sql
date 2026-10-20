-- 修复两个菜单-权限目录不变量（MenuModuleInitializationTest / MenuCatalogUpgradeTest）：
-- 1) V122 澄清组织层级动作名称时，权限行更新条件 client_type='WEB' 漏掉 V48 以 client_type='BOTH'
--    建立的四个 CLASS_* 权限行，权限名停留在"班级管理 · ..."与菜单名不一致；
-- 2) V118 规范化后 37 个 BUTTON 权限缺少 SYS_ADMIN 授权，违反
--    migrationMapsEveryButtonAndGrantsOnlyAdministratorNewActions 的既有不变量。

UPDATE sys_permission target
JOIN sys_menu menu ON menu.code = target.permission_code AND menu.type = 'BUTTON'
SET target.permission_name = menu.name
WHERE target.permission_code IN ('CLASS_CREATE', 'CLASS_READ', 'CLASS_UPDATE', 'CLASS_STATUS_CHANGE');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT 1874300000000144000 + ROW_NUMBER() OVER (ORDER BY p.permission_code),
       r.id, p.id, 'ALLOW'
FROM sys_role r
JOIN sys_menu m ON m.type = 'BUTTON'
JOIN sys_permission p ON p.permission_code = m.code
WHERE r.role_code = 'SYS_ADMIN'
  AND r.status = 'ENABLED'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id);
