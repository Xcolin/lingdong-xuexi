-- 周报主动订阅偏好；不代表已获得微信消息许可或已投递。
CREATE TABLE growth_review_subscription (
    id BIGINT NOT NULL,
    parent_user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT FALSE,
    version_no BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_growth_review_subscription_owner UNIQUE (parent_user_id, student_id),
    CONSTRAINT fk_growth_review_subscription_parent FOREIGN KEY (parent_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_growth_review_subscription_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT ck_growth_review_subscription_id CHECK (id >= 1000000000000000000),
    CONSTRAINT ck_growth_review_subscription_version CHECK (version_no >= 1)
);
CREATE INDEX idx_growth_review_subscription_enabled ON growth_review_subscription(enabled_flag, id);

INSERT INTO sys_feature_toggle(id, feature_code, feature_name, scope_type, scope_key, status, built_in, description)
VALUES (1874244142494646691, 'GROWTH_REVIEW_WEEKLY_SUBSCRIPTION', '每周周报订阅', 'GLOBAL', 'GLOBAL', 'DISABLED', 1,
    '控制新订阅及后续排程；停用不阻止家长取消本人订阅，微信渠道另行验收。');
INSERT INTO sys_permission(id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
VALUES (1874244142494646692, 'GROWTH_REVIEW_SUBSCRIBE_CHILD', '订阅孩子周报', 'OPERATION', 'WEB', NULL, 260, 'ENABLED',
    '活动关系家长主动订阅孩子周报；不包含其他家长订阅管理权限。');
INSERT INTO sys_role_permission(id, role_id, permission_id)
SELECT 1874244142494646693, id, 1874244142494646692 FROM sys_role WHERE role_code='PARENT';
