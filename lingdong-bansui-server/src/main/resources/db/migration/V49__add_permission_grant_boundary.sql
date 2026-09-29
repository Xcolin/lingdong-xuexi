-- V49：为角色权限增加明确允许和明确禁止效果，支撑多角色禁止优先决策。
-- 既有角色权限均为允许授权，新增字段默认并回填为 ALLOW。

ALTER TABLE sys_role_permission
    ADD COLUMN effect VARCHAR(16) NOT NULL DEFAULT 'ALLOW';

ALTER TABLE sys_role_permission
    ADD CONSTRAINT ck_sys_role_permission_effect
        CHECK (effect IN ('ALLOW', 'DENY'));
