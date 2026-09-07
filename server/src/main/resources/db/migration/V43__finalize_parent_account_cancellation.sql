-- V43：家长账号最终注销的到期扫描与安全重试状态。
-- 本迁移不新增业务表；既有和后续主键仍由应用层生成 19 位雪花数字。

ALTER TABLE auth_parent_account_cancellation
    ADD COLUMN finalization_attempts INT NOT NULL DEFAULT 0;

ALTER TABLE auth_parent_account_cancellation
    ADD COLUMN next_finalize_at TIMESTAMP NULL;

ALTER TABLE auth_parent_account_cancellation
    ADD COLUMN last_finalize_error_code VARCHAR(64) NULL;

UPDATE auth_parent_account_cancellation
SET next_finalize_at = cooling_ends_at
WHERE status = 'COOLING_OFF'
  AND active_scope_key = 'ACTIVE'
  AND next_finalize_at IS NULL;

CREATE INDEX idx_parent_account_cancellation_due
    ON auth_parent_account_cancellation (status, next_finalize_at, id);

UPDATE sys_feature_toggle
SET description = '控制家长手机号换绑、注销申请、冷静期撤销及到期最终注销。',
    updated_at = CURRENT_TIMESTAMP
WHERE feature_code = 'PARENT_ACCOUNT_LIFECYCLE'
  AND scope_key = 'GLOBAL';
