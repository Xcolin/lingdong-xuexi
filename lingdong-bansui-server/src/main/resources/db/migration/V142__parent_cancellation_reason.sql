-- 8.4 账号注销独立表单：记录注销原因与补充说明（对照 07-10）
ALTER TABLE auth_parent_account_cancellation
    ADD COLUMN reason VARCHAR(64) NULL;

ALTER TABLE auth_parent_account_cancellation
    ADD COLUMN reason_note VARCHAR(255) NULL;
