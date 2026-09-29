-- V27：学生独立家庭奖励库、兑换事实和最小访问权限。

CREATE TABLE growth_reward (
    id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    created_by_parent_id BIGINT NOT NULL,
    reward_name VARCHAR(30) NOT NULL,
    required_points BIGINT NOT NULL,
    description VARCHAR(200) NULL,
    expires_at TIMESTAMP NULL,
    status VARCHAR(16) NOT NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_growth_reward_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_growth_reward_creator
        FOREIGN KEY (created_by_parent_id) REFERENCES sys_user (id),
    CONSTRAINT ck_growth_reward_name CHECK (
        CHAR_LENGTH(TRIM(reward_name)) BETWEEN 1 AND 30
    ),
    CONSTRAINT ck_growth_reward_points CHECK (required_points > 0),
    CONSTRAINT ck_growth_reward_status CHECK (
        status IN ('ONLINE', 'OFFLINE', 'DELETED')
    )
);

CREATE INDEX idx_growth_reward_student_status
    ON growth_reward (student_id, status, expires_at, id);

CREATE TABLE growth_reward_exchange (
    id BIGINT NOT NULL,
    reward_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    requester_user_id BIGINT NOT NULL,
    reward_name_snapshot VARCHAR(30) NOT NULL,
    required_points_snapshot BIGINT NOT NULL,
    description_snapshot VARCHAR(200) NULL,
    requested_at TIMESTAMP NOT NULL,
    approval_deadline TIMESTAMP NOT NULL,
    status VARCHAR(32) NOT NULL,
    reviewed_by BIGINT NULL,
    reviewed_at TIMESTAMP NULL,
    reject_reason VARCHAR(500) NULL,
    verified_by BIGINT NULL,
    verified_at TIMESTAMP NULL,
    version_no INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_growth_reward_exchange_reward
        FOREIGN KEY (reward_id) REFERENCES growth_reward (id),
    CONSTRAINT fk_growth_reward_exchange_student
        FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_growth_reward_exchange_requester
        FOREIGN KEY (requester_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_growth_reward_exchange_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES sys_user (id),
    CONSTRAINT fk_growth_reward_exchange_verifier
        FOREIGN KEY (verified_by) REFERENCES sys_user (id),
    CONSTRAINT ck_growth_reward_exchange_points CHECK (required_points_snapshot > 0),
    CONSTRAINT ck_growth_reward_exchange_deadline CHECK (approval_deadline > requested_at),
    CONSTRAINT ck_growth_reward_exchange_status CHECK (
        status IN ('PENDING_APPROVAL', 'PENDING_VERIFICATION', 'REJECTED',
            'AUTO_REJECTED', 'EXPIRED', 'VERIFIED')
    ),
    CONSTRAINT ck_growth_reward_exchange_rejection CHECK (
        status <> 'REJECTED'
        OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
            AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason)) BETWEEN 1 AND 500)
    )
);

CREATE INDEX idx_growth_reward_exchange_student_status
    ON growth_reward_exchange (student_id, status, requested_at, id);
CREATE INDEX idx_growth_reward_exchange_reward_status
    ON growth_reward_exchange (reward_id, status, approval_deadline, id);
CREATE INDEX idx_growth_reward_exchange_pending_deadline
    ON growth_reward_exchange (status, approval_deadline, id);

ALTER TABLE growth_point_ledger
    ADD COLUMN source_exchange_id BIGINT NULL;

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT uk_growth_point_ledger_exchange UNIQUE (source_exchange_id);

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT fk_growth_point_ledger_exchange
        FOREIGN KEY (source_exchange_id) REFERENCES growth_reward_exchange (id);

ALTER TABLE growth_point_ledger
    DROP CONSTRAINT ck_growth_point_ledger_amount;

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_amount CHECK (
        (change_type IN ('REDEMPTION', 'DORMANCY_CLEAR') AND amount = 0)
        OR (change_type NOT IN ('REDEMPTION', 'DORMANCY_CLEAR') AND amount <> 0)
    );

ALTER TABLE growth_point_ledger
    ADD CONSTRAINT ck_growth_point_ledger_redemption CHECK (
        (change_type = 'REDEMPTION'
            AND source_exchange_id IS NOT NULL
            AND source_assignment_id IS NULL
            AND source_type = 'FAMILY'
            AND source_organization_id IS NULL
            AND reviewer_user_id IS NOT NULL
            AND amount = 0
            AND available_delta < 0
            AND correction_of_id IS NULL)
        OR (change_type <> 'REDEMPTION' AND source_exchange_id IS NULL)
    );

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646383, 'REWARD_EXCHANGE', '家庭奖励与积分兑换',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制主家长奖励配置、学生兑换申请、主家长审批和核销能力。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646384, 'REWARD_MANAGE_CHILD', '管理孩子家庭奖励',
        'OPERATION', 'WEB', NULL, 190, 'ENABLED',
        '主家长在 Web 管理活动主关系学生的家庭奖励。'),
    (1874244142494646385, 'REWARD_EXCHANGE_REVIEW_CHILD', '处理孩子奖励兑换',
        'OPERATION', 'WEB', NULL, 200, 'ENABLED',
        '主家长在 Web 审批、驳回和核销活动主关系学生的兑换记录。'),
    (1874244142494646386, 'REWARD_EXCHANGE_SELF', '申请本人奖励兑换',
        'OPERATION', 'MINIAPP', NULL, 210, 'ENABLED',
        '学生在小程序浏览本人奖励、提交申请并查询兑换记录。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646387, 1874244142494646277, 1874244142494646384),
    (1874244142494646388, 1874244142494646277, 1874244142494646385),
    (1874244142494646389, 1874244142494646278, 1874244142494646386);
