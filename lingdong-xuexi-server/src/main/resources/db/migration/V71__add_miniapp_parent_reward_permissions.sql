-- 家长小程序奖励和兑换权限独立于 WEB，沿用既有主副家长业务边界。
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES
(1874244142494647101,'MINIAPP_REWARD_MANAGE_CHILD','小程序管理孩子家庭奖励','OPERATION','MINIAPP',NULL,273,'ENABLED','活动家长可读，活动主家长可管理家庭奖励。'),
(1874244142494647102,'MINIAPP_REWARD_EXCHANGE_REVIEW_CHILD','小程序处理孩子奖励兑换','OPERATION','MINIAPP',NULL,274,'ENABLED','活动家长可读，活动主家长审批、驳回和核销奖励兑换。');
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494647103,id,1874244142494647101 FROM sys_role WHERE role_code='PARENT';
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494647104,id,1874244142494647102 FROM sys_role WHERE role_code='PARENT';
