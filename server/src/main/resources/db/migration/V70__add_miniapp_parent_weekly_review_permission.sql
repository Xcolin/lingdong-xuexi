-- 家长小程序周报只读权限独立于 WEB；不授权补录、订阅或消息发送。
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES(1874244142494647001,'MINIAPP_GROWTH_REVIEW_READ_CHILD','小程序查看孩子历史周报','OPERATION','MINIAPP',NULL,272,'ENABLED','仅当前活动关系家长读取孩子周报，逐次核验动态权限；不代表消息授权。');
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494647002,id,1874244142494647001 FROM sys_role WHERE role_code='PARENT';
