-- 奖励兑换报表仅授予家长独立导出权限，活动主家长关系仍由每个读取节点核验。
ALTER TABLE sys_export_job DROP CONSTRAINT ck_sys_export_job_type;
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_type
    CHECK (export_type IN ('GROWTH_POINT_LEDGER', 'IAM_CHANGE_AUDIT', 'GROWTH_REVIEW_PDF', 'DICTIONARY_LEDGER', 'TEMPLATE_LEDGER', 'INTERFACE_SERVICE_LEDGER', 'CACHE_OPERATION_LOG', 'SYSTEM_TASK_LEDGER', 'REWARD_EXCHANGE_LEDGER'));
ALTER TABLE sys_export_job ADD CONSTRAINT ck_sys_export_job_reward_scope
    CHECK (export_type <> 'REWARD_EXCHANGE_LEDGER' OR (student_id IS NOT NULL AND system_task_id IS NULL AND sensitive_flag = 0));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES (1874244142494648101,'REWARD_EXCHANGE_EXPORT','导出奖励兑换报表','OPERATION','WEB',NULL,360,'ENABLED','仅活动主家长生成、读取和下载学生奖励兑换事实报表。');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
SELECT 1874244142494648102,id,1874244142494648101,'ALLOW' FROM sys_role WHERE role_code='PARENT';
INSERT INTO sys_dictionary_item (id,type_id,item_code,item_name,sort_order,is_default,status)
VALUES (1874244142494648103,1874244142494646611,'REWARD_EXCHANGE_REPORT','奖励兑换报表',90,0,'ENABLED');
