-- Every configured button is independently grantable; actionKey codes stay unchanged.
-- Keep existing business permission definitions and grants intact. Allocate IDs above
-- the current table maximum, ordered by menu ID, so historical seed ranges cannot collide.
INSERT INTO sys_permission
    (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY m.id), m.code, m.name,
       'BUTTON', 'WEB', NULL, m.sort_order, m.status, '菜单按钮独立授权权限'
FROM sys_menu m
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_permission) base
WHERE m.type = 'BUTTON'
  AND NOT EXISTS (SELECT 1 FROM sys_permission p WHERE p.permission_code = m.code);

UPDATE sys_menu SET grantable = 1, permission_code = code, version = version + 1
WHERE type = 'BUTTON' AND (grantable <> 1 OR permission_code IS NULL OR permission_code <> code);

-- Only SYS_ADMIN receives missing configured button grants. Preserve DENY entries.
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT base.max_id + ROW_NUMBER() OVER (ORDER BY m.id, r.id), r.id, p.id, 'ALLOW'
FROM sys_menu m
JOIN sys_permission p ON p.permission_code = m.code
JOIN sys_role r ON r.role_code = 'SYS_ADMIN'
CROSS JOIN (SELECT COALESCE(MAX(id), 0) AS max_id FROM sys_role_permission) base
WHERE m.type = 'BUTTON'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
