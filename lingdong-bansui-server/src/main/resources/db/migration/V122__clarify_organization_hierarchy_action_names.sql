-- Clarify hierarchy action labels; preserve grants.
UPDATE sys_menu SET name='组织层级 · 新增校区、年级、班级 / 课程分类、课程班',version=version+1 WHERE type='BUTTON' AND code='CLASS_CREATE' AND name<>'组织层级 · 新增校区、年级、班级 / 课程分类、课程班';
UPDATE sys_permission SET permission_name='组织层级 · 新增校区、年级、班级 / 课程分类、课程班' WHERE client_type='WEB' AND permission_code='CLASS_CREATE';
UPDATE sys_menu SET name='组织层级 · 查询校区、年级、班级 / 课程分类、课程班',version=version+1 WHERE type='BUTTON' AND code='CLASS_READ' AND name<>'组织层级 · 查询校区、年级、班级 / 课程分类、课程班';
UPDATE sys_permission SET permission_name='组织层级 · 查询校区、年级、班级 / 课程分类、课程班' WHERE client_type='WEB' AND permission_code='CLASS_READ';
UPDATE sys_menu SET name='组织层级 · 编辑校区、年级、班级 / 课程分类、课程班',version=version+1 WHERE type='BUTTON' AND code='CLASS_UPDATE' AND name<>'组织层级 · 编辑校区、年级、班级 / 课程分类、课程班';
UPDATE sys_permission SET permission_name='组织层级 · 编辑校区、年级、班级 / 课程分类、课程班' WHERE client_type='WEB' AND permission_code='CLASS_UPDATE';
UPDATE sys_menu SET name='组织层级 · 启停校区、年级、班级 / 课程分类、课程班',version=version+1 WHERE type='BUTTON' AND code='CLASS_STATUS_CHANGE' AND name<>'组织层级 · 启停校区、年级、班级 / 课程分类、课程班';
UPDATE sys_permission SET permission_name='组织层级 · 启停校区、年级、班级 / 课程分类、课程班' WHERE client_type='WEB' AND permission_code='CLASS_STATUS_CHANGE';
