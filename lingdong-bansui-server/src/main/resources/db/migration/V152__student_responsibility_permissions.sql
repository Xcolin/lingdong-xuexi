-- New actions belong to the actual organization page, never to another button.
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,status)
SELECT 2201000000000152001,'STUDENT_RESPONSIBILITY_READ','学员负责人 · 查看与记录','BUTTON','BOTH',id,'ENABLED'
FROM sys_permission WHERE permission_code='ORG_NODE_READ';
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,status)
SELECT 2201000000000152002,'STUDENT_RESPONSIBILITY_TRANSFER','学员负责人 · 转交','BUTTON','BOTH',id,'ENABLED'
FROM sys_permission WHERE permission_code='ORG_NODE_READ';
INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version)
SELECT p.id,p.permission_code,p.permission_name,'BUTTON',m.id,p.permission_code,1,
CASE WHEN p.permission_code='STUDENT_RESPONSIBILITY_READ' THEN 960 ELSE 961 END,'ENABLED',0
FROM sys_permission p JOIN sys_permission parent ON parent.id=p.parent_id
JOIN sys_menu m ON m.code=parent.permission_code AND m.type='PAGE'
WHERE p.permission_code IN('STUDENT_RESPONSIBILITY_READ','STUDENT_RESPONSIBILITY_TRANSFER');

-- Carry only established read/manage grants with their enabled organization page.
-- Explicit deny is copied as deny, including when the page is not granted.
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY rp.role_id,p.id),rp.role_id,p.id,rp.effect
FROM sys_role_permission rp JOIN sys_permission old ON old.id=rp.permission_id
JOIN sys_permission p ON (old.permission_code='CLASS_READ' AND p.permission_code='STUDENT_RESPONSIBILITY_READ')
 OR (old.permission_code='STUDENT_ORGANIZATION_MANAGE' AND p.permission_code='STUDENT_RESPONSIBILITY_TRANSFER')
JOIN sys_role r ON r.id=rp.role_id AND r.status='ENABLED'
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE (rp.effect='DENY' OR (rp.effect='ALLOW' AND EXISTS(
 SELECT 1 FROM sys_role_permission page WHERE page.role_id=rp.role_id AND page.permission_id=p.parent_id AND page.effect='ALLOW')))
AND NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=rp.role_id AND existing.permission_id=p.id);

INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY up.user_id,p.id),up.user_id,p.id,up.effect
FROM sys_user_permission up JOIN sys_permission old ON old.id=up.permission_id
JOIN sys_permission p ON (old.permission_code='CLASS_READ' AND p.permission_code='STUDENT_RESPONSIBILITY_READ')
 OR (old.permission_code='STUDENT_ORGANIZATION_MANAGE' AND p.permission_code='STUDENT_RESPONSIBILITY_TRANSFER')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_user_permission) base
WHERE (up.effect='DENY' OR (up.effect='ALLOW' AND EXISTS(
 SELECT 1 FROM sys_user_permission page WHERE page.user_id=up.user_id AND page.permission_id=p.parent_id AND page.effect='ALLOW')))
AND NOT EXISTS(SELECT 1 FROM sys_user_permission existing WHERE existing.user_id=up.user_id AND existing.permission_id=p.id);
