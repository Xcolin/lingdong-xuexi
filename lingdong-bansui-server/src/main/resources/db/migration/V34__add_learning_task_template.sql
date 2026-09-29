CREATE TABLE learn_task_template (
    id BIGINT NOT NULL,
    template_scope VARCHAR(16) NOT NULL,
    owner_user_id BIGINT NULL,
    owner_scope_key VARCHAR(32) NOT NULL,
    template_name VARCHAR(50) NOT NULL,
    active_name_key VARCHAR(50) NULL,
    task_title VARCHAR(50) NOT NULL,
    difficulty_level INT NOT NULL,
    duration_minutes INT NOT NULL,
    category_code VARCHAR(64) NULL,
    remark VARCHAR(200) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by_user_id BIGINT NULL,
    updated_by_user_id BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_template_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_template_created_user
        FOREIGN KEY (created_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_task_template_updated_user
        FOREIGN KEY (updated_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT uk_task_template_active_name
        UNIQUE (owner_scope_key, active_name_key),
    CONSTRAINT ck_task_template_scope
        CHECK (template_scope IN ('SYSTEM', 'PERSONAL')),
    CONSTRAINT ck_task_template_owner
        CHECK ((template_scope = 'SYSTEM' AND owner_user_id IS NULL AND owner_scope_key = 'SYSTEM')
            OR (template_scope = 'PERSONAL' AND owner_user_id IS NOT NULL AND owner_scope_key <> 'SYSTEM')),
    CONSTRAINT ck_task_template_name
        CHECK (CHAR_LENGTH(template_name) BETWEEN 1 AND 50),
    CONSTRAINT ck_task_template_active_name
        CHECK ((status = 'ENABLED' AND active_name_key = template_name)
            OR (status = 'DELETED' AND active_name_key IS NULL)),
    CONSTRAINT ck_task_template_title
        CHECK (CHAR_LENGTH(task_title) BETWEEN 1 AND 50),
    CONSTRAINT ck_task_template_difficulty
        CHECK (difficulty_level BETWEEN 1 AND 3),
    CONSTRAINT ck_task_template_duration
        CHECK (duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_task_template_sort
        CHECK (sort_order >= 0),
    CONSTRAINT ck_task_template_status
        CHECK (status IN ('ENABLED', 'DELETED')),
    CONSTRAINT ck_task_template_version
        CHECK (version_no >= 1)
);

CREATE INDEX idx_task_template_owner_status_sort
    ON learn_task_template (owner_user_id, status, sort_order, id);
CREATE INDEX idx_task_template_scope_status_sort
    ON learn_task_template (template_scope, status, sort_order, id);

CREATE TABLE learn_task_template_tag (
    id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    tag_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_task_template_tag_template
        FOREIGN KEY (template_id) REFERENCES learn_task_template (id),
    CONSTRAINT uk_task_template_tag
        UNIQUE (template_id, tag_code),
    CONSTRAINT ck_task_template_tag_code
        CHECK (CHAR_LENGTH(tag_code) BETWEEN 1 AND 64)
);

CREATE INDEX idx_task_template_tag_template
    ON learn_task_template_tag (template_id, id);

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646518, 'LEARNING_TASK_TEMPLATE', '学习任务模板', 'GLOBAL', 'GLOBAL',
        'ENABLED', 1, '控制系统常用任务模板和家长个人模板能力。');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646519, 'LEARNING_TASK_TEMPLATE_READ', '查询学习任务模板',
        'OPERATION', 'WEB', NULL, 250, 'ENABLED',
        '家长查询系统常用模板和本人个人模板。'),
    (1874244142494646520, 'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL', '管理个人任务模板',
        'OPERATION', 'WEB', NULL, 260, 'ENABLED',
        '家长新增、编辑、删除和排序本人个人模板。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646521, 1874244142494646277, 1874244142494646519),
    (1874244142494646522, 1874244142494646277, 1874244142494646520);

INSERT INTO learn_task_template (
    id, template_scope, owner_user_id, owner_scope_key, template_name,
    active_name_key, task_title, difficulty_level, duration_minutes,
    category_code, remark, sort_order, status, version_no,
    created_by_user_id, updated_by_user_id
) VALUES
    (1874244142494646523, 'SYSTEM', NULL, 'SYSTEM', '每日阅读30分钟',
        '每日阅读30分钟', '每日阅读30分钟', 1, 30, 'GENERAL', NULL, 10,
        'ENABLED', 1, NULL, NULL),
    (1874244142494646524, 'SYSTEM', NULL, 'SYSTEM', '口算练习',
        '口算练习', '口算练习', 1, 15, 'GENERAL', NULL, 20,
        'ENABLED', 1, NULL, NULL);

INSERT INTO learn_task_template_tag (id, template_id, tag_code) VALUES
    (1874244142494646525, 1874244142494646523, 'DAILY'),
    (1874244142494646526, 1874244142494646524, 'DAILY');
