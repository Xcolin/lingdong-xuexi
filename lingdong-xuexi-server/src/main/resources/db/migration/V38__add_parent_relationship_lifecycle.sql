-- V38：副家长邀请、主副家长关系变更和不可变审计基础。
-- 所有主键均由应用层雪花算法生成，迁移不使用数据库自增列。

ALTER TABLE edu_parent_student
    MODIFY COLUMN primary_scope_key VARCHAR(32) NOT NULL;

ALTER TABLE edu_parent_student
    MODIFY COLUMN relation_role VARCHAR(32) NOT NULL;

CREATE TABLE edu_parent_relationship_invitation (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    invitee_user_id BIGINT NULL,
    invitee_mobile VARCHAR(20) NOT NULL,
    invitation_type VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    pending_scope_key VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP NULL,
    responded_by_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_parent_relationship_invitation_pending
        UNIQUE (student_id, invitation_type, pending_scope_key),
    CONSTRAINT fk_parent_relationship_invitation_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_parent_relationship_invitation_inviter
        FOREIGN KEY (inviter_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_parent_relationship_invitation_invitee
        FOREIGN KEY (invitee_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_parent_relationship_invitation_responder
        FOREIGN KEY (responded_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_parent_relationship_invitation_type
        CHECK (invitation_type IN ('SECONDARY_BIND', 'PRIMARY_TRANSFER')),
    CONSTRAINT ck_parent_relationship_invitation_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'))
);

CREATE INDEX idx_parent_relationship_invitation_target
    ON edu_parent_relationship_invitation (invitee_mobile, status, expires_at);
CREATE INDEX idx_parent_relationship_invitation_student
    ON edu_parent_relationship_invitation (student_id, status, created_at);

CREATE TABLE edu_parent_relationship_change_log (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    relationship_id BIGINT NULL,
    invitation_id BIGINT NULL,
    operation_type VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    previous_parent_user_id BIGINT NULL,
    new_parent_user_id BIGINT NULL,
    previous_role VARCHAR(32) NULL,
    new_role VARCHAR(32) NULL,
    occurred_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_parent_relationship_change_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_parent_relationship_change_relationship
        FOREIGN KEY (relationship_id) REFERENCES edu_parent_student (id),
    CONSTRAINT fk_parent_relationship_change_invitation
        FOREIGN KEY (invitation_id) REFERENCES edu_parent_relationship_invitation (id),
    CONSTRAINT fk_parent_relationship_change_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_parent_relationship_change_previous_parent
        FOREIGN KEY (previous_parent_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_parent_relationship_change_new_parent
        FOREIGN KEY (new_parent_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_parent_relationship_change_operation
        CHECK (operation_type IN ('BIND_SECONDARY', 'UNBIND_SECONDARY',
            'UNBIND_PRIMARY_PROMOTE', 'UNBIND_PRIMARY_ORPHAN',
            'TRANSFER_PRIMARY', 'REBIND_PRIMARY'))
);

CREATE INDEX idx_parent_relationship_change_student_time
    ON edu_parent_relationship_change_log (student_id, occurred_at);
CREATE INDEX idx_parent_relationship_change_operator_time
    ON edu_parent_relationship_change_log (operator_user_id, occurred_at);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646534, 'PARENT_RELATIONSHIP_MANAGEMENT', '家长关系管理',
        'GLOBAL', 'GLOBAL', 'DISABLED', 1,
        '控制副家长邀请、关系解绑、主家长晋升和监护权转移。');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646535, 'PARENT_RELATIONSHIP_READ', '查询家长关系',
        'OPERATION', 'BOTH', NULL, 210, 'ENABLED', '查询本人活动关系学生的主副家长关系。'),
    (1874244142494646536, 'SECONDARY_PARENT_INVITE_CREATE', '邀请副家长',
        'OPERATION', 'BOTH', NULL, 220, 'ENABLED', '活动主家长邀请副家长绑定同一学生。'),
    (1874244142494646537, 'PARENT_RELATIONSHIP_INVITE_RESPOND', '响应家长关系邀请',
        'OPERATION', 'BOTH', NULL, 230, 'ENABLED', '目标手机号持有人接受或拒绝关系邀请。'),
    (1874244142494646538, 'SECONDARY_PARENT_UNBIND', '解除副家长关系',
        'OPERATION', 'BOTH', NULL, 240, 'ENABLED', '活动主家长解除当前副家长关系。'),
    (1874244142494646539, 'PRIMARY_PARENT_UNBIND', '主家长解绑',
        'OPERATION', 'BOTH', NULL, 250, 'ENABLED', '活动主家长解除自身关系并按规则晋升副家长。'),
    (1874244142494646540, 'PRIMARY_PARENT_TRANSFER', '转移主家长',
        'OPERATION', 'BOTH', NULL, 260, 'ENABLED', '原主家长发起且目标家长确认监护权转移。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646541, 1874244142494646277, 1874244142494646535),
    (1874244142494646542, 1874244142494646277, 1874244142494646536),
    (1874244142494646543, 1874244142494646277, 1874244142494646537),
    (1874244142494646544, 1874244142494646277, 1874244142494646538),
    (1874244142494646545, 1874244142494646277, 1874244142494646539),
    (1874244142494646546, 1874244142494646277, 1874244142494646540);
