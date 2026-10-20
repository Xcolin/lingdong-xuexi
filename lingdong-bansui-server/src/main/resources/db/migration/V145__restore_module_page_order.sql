-- V118 重建菜单时沿用全局递增 sort_order，破坏了 V100 确立的目录内页面顺序语义
-- （权限管理：组织/用户/角色/菜单；系统设置：字典/缓存/功能开关；导入导出：模板/导入/导出）。
-- 恢复目录内相对顺序，保持全局编码间距以便后续插入。
UPDATE sys_menu m
JOIN sys_menu p ON p.id = m.parent_id AND p.code = 'PERMISSION_MANAGEMENT'
SET m.sort_order = CASE m.route WHEN '/organizations' THEN 10 WHEN '/users' THEN 20 WHEN '/iam' THEN 30 ELSE 40 END,
    m.version = m.version + 1
WHERE m.type = 'PAGE' AND m.route IN ('/organizations', '/users', '/iam', '/menu-management');

UPDATE sys_menu m
JOIN sys_menu p ON p.id = m.parent_id AND p.code = 'SYSTEM_SETTINGS'
SET m.sort_order = CASE m.route WHEN '/dictionaries' THEN 10 WHEN '/cache-management' THEN 20 ELSE 30 END,
    m.version = m.version + 1
WHERE m.type = 'PAGE' AND m.route IN ('/dictionaries', '/cache-management', '/feature-management');

UPDATE sys_menu m
JOIN sys_menu p ON p.id = m.parent_id AND p.code = 'IMPORT_EXPORT_MANAGEMENT'
SET m.sort_order = CASE m.route WHEN '/import-export-templates' THEN 10 WHEN '/import-jobs' THEN 20 ELSE 30 END,
    m.version = m.version + 1
WHERE m.type = 'PAGE' AND m.route IN ('/import-export-templates', '/import-jobs', '/export-jobs');
