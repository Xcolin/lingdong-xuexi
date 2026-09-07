-- V47：组织节点自身状态、有效状态、高风险变更申请和不可变审计。
-- 所有主键与基础数据标识均为 19 位雪花数字，不使用数据库自增列。

ALTER TABLE sys_organization
    ADD COLUMN effective_status VARCHAR(16) NOT NULL DEFAULT 'ENABLED';

ALTER TABLE sys_organization
    ADD COLUMN version_no INT NOT NULL DEFAULT 1;

ALTER TABLE sys_organization
    ADD CONSTRAINT ck_sys_organization_effective_status
        CHECK (effective_status IN ('ENABLED', 'DISABLED'));

ALTER TABLE sys_organization
    ADD CONSTRAINT ck_sys_organization_version
        CHECK (version_no > 0);

-- 兼容已有停用节点：自身或任一祖先停用时，初始有效状态均为停用。
UPDATE sys_organization
SET effective_status = 'DISABLED'
WHERE id IN (
    SELECT organization_id
    FROM (
        SELECT DISTINCT target.id AS organization_id
        FROM sys_organization target
        JOIN sys_organization ancestor
          ON target.organization_path LIKE CONCAT(ancestor.organization_path, '%')
        WHERE target.status = 'DISABLED'
           OR ancestor.status = 'DISABLED'
    ) disabled_organizations
);

CREATE TABLE sys_organization_change (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    change_type VARCHAR(16) NOT NULL,
    target_parent_id BIGINT NULL,
    expected_version INT NOT NULL,
    organization_code_snapshot VARCHAR(64) NOT NULL,
    organization_name_snapshot VARCHAR(128) NOT NULL,
    from_parent_id_snapshot BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    execution_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    failure_reason VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_sys_organization_change_task UNIQUE (task_id),
    CONSTRAINT ck_sys_organization_change_type
        CHECK (change_type IN ('DISABLE', 'MOVE', 'DELETE')),
    CONSTRAINT ck_sys_organization_change_execution
        CHECK (execution_status IN ('PENDING', 'APPLIED', 'FAILED')),
    CONSTRAINT ck_sys_organization_change_version CHECK (expected_version > 0),
    CONSTRAINT fk_sys_organization_change_task
        FOREIGN KEY (task_id) REFERENCES sys_system_task (id)
);

CREATE INDEX idx_sys_organization_change_target
    ON sys_organization_change (organization_id, execution_status, created_at);

CREATE TABLE sys_organization_change_audit (
    id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    task_id BIGINT NULL,
    event_type VARCHAR(24) NOT NULL,
    change_type VARCHAR(16) NULL,
    before_name VARCHAR(128) NULL,
    after_name VARCHAR(128) NULL,
    before_sort_order INT NULL,
    after_sort_order INT NULL,
    before_parent_id BIGINT NULL,
    after_parent_id BIGINT NULL,
    before_status VARCHAR(16) NULL,
    after_status VARCHAR(16) NULL,
    operator_user_id BIGINT NOT NULL,
    reason VARCHAR(500) NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_sys_organization_change_audit_event
        CHECK (event_type IN ('DIRECT_UPDATE', 'ENABLE', 'REQUEST', 'APPLY', 'REJECT', 'EXECUTION_FAILED')),
    CONSTRAINT ck_sys_organization_change_audit_type
        CHECK (change_type IS NULL OR change_type IN ('DISABLE', 'MOVE', 'DELETE')),
    CONSTRAINT fk_sys_organization_change_audit_task
        FOREIGN KEY (task_id) REFERENCES sys_system_task (id),
    CONSTRAINT fk_sys_organization_change_audit_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_sys_organization_change_audit_target_time
    ON sys_organization_change_audit (organization_id, occurred_at, id);

CREATE INDEX idx_sys_organization_change_audit_task
    ON sys_organization_change_audit (task_id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646564, 'ORGANIZATION_MANAGEMENT', '组织管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制组织树查询、编辑、启停、移动、删除申请和组织变更审核。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646565, 'ORG_NODE_UPDATE', '编辑组织节点',
     'OPERATION', 'WEB', NULL, 50, 'ENABLED', '编辑组织名称和排序，或重新启用组织节点。'),
    (1874244142494646566, 'ORG_NODE_CHANGE_SUBMIT', '提交组织变更',
     'OPERATION', 'WEB', NULL, 60, 'ENABLED', '系统管理员提交组织停用、移动或受控删除申请。'),
    (1874244142494646567, 'ORG_NODE_CHANGE_REVIEW', '审核组织变更',
     'OPERATION', 'WEB', NULL, 70, 'ENABLED', '系统审核员审核系统管理员提交的组织变更。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646568, 1874244142494646273, 1874244142494646565),
    (1874244142494646569, 1874244142494646273, 1874244142494646566),
    (1874244142494646570, 1874244142494646274, 1874244142494646567);
