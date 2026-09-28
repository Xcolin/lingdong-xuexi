-- 仅新申请保存变更前快照；历史申请保持 NULL，禁止以当前值回填历史。
ALTER TABLE sys_interface_service_change ADD COLUMN before_status VARCHAR(16) NULL;
ALTER TABLE sys_interface_service_change ADD COLUMN before_authorization_scope VARCHAR(32) NULL;
ALTER TABLE sys_interface_service_change ADD COLUMN before_authorization_scope_value VARCHAR(128) NULL;
