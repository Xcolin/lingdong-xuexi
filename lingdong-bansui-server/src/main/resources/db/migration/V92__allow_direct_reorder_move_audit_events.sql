-- 组织拖拽直接生效审计：同级排序（DIRECT_REORDER）与改父级移动（DIRECT_MOVE）
-- 扩展组织变更审计事件类型约束（沿用 V48 的约束重定义模式）

ALTER TABLE sys_organization_change_audit
DROP CONSTRAINT ck_sys_organization_change_audit_event;

ALTER TABLE sys_organization_change_audit
ADD CONSTRAINT ck_sys_organization_change_audit_event CHECK (
        event_type IN ('CREATE', 'DIRECT_UPDATE', 'DIRECT_REORDER', 'DIRECT_MOVE',
            'ENABLE', 'DISABLE', 'REQUEST', 'APPLY', 'REJECT', 'EXECUTION_FAILED')
    );
