-- V20：机构邀请既有家长账号绑定学生。
-- 原始邀请令牌绝不入库；所有主键由应用层雪花算法生成。

CREATE TABLE edu_parent_binding_invitation (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    inviter_user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    pending_scope_key VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP NULL,
    responded_by_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_edu_parent_invitation_token UNIQUE (token_hash),
    CONSTRAINT uk_edu_parent_invitation_pending UNIQUE (student_id, pending_scope_key),
    CONSTRAINT fk_edu_parent_invitation_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_edu_parent_invitation_organization FOREIGN KEY (organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_edu_parent_invitation_inviter FOREIGN KEY (inviter_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_edu_parent_invitation_responder FOREIGN KEY (responded_by_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_edu_parent_invitation_student_status ON edu_parent_binding_invitation (student_id, status);
CREATE INDEX idx_edu_parent_invitation_expiry_status ON edu_parent_binding_invitation (expires_at, status);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description
) VALUES
    (1874244142494646331, 'STUDENT_PARENT_INVITE_CREATE', '创建家长绑定邀请', 'OPERATION', 'WEB', NULL, 30, 'ENABLED', '机构管理员为直管学生创建家长绑定邀请。'),
    (1874244142494646332, 'STUDENT_PARENT_INVITE_RESPOND', '响应家长绑定邀请', 'OPERATION', 'WEB', NULL, 40, 'ENABLED', '家长接受或拒绝有效的家长绑定邀请。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646333, 1874244142494646275, 1874244142494646331),
    (1874244142494646334, 1874244142494646277, 1874244142494646332);
