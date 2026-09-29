-- 小程序独立授权，保留 V68 的 WEB 权限边界及功能默认关闭。
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES(1874244142494646901,'MINIAPP_ANONYMOUS_CLASS_RANK_READ','小程序查看孩子班级匿名排行','OPERATION','MINIAPP',NULL,271,'ENABLED','实际小程序家长会话逐次核验动态权限、亲子关系及当前班级。');
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494646902,id,1874244142494646901 FROM sys_role WHERE role_code='PARENT';
