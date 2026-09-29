-- 系统工作台只读权限不授予任何领域审批或执行能力。
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES(1874244142494647201,'SYSTEM_TASK_READ','查询系统任务工作台','OPERATION','WEB',NULL,275,'ENABLED','系统管理员只看本人任务，系统审核员查看已提交任务及审批历史。');
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494647202,id,1874244142494647201 FROM sys_role WHERE role_code='SYS_ADMIN';
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494647203,id,1874244142494647201 FROM sys_role WHERE role_code='SYS_AUDITOR';
