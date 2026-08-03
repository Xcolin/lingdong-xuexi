-- V22：学习任务定义、目标、学生任务实例及班级关系基础。
-- 所有业务表主键由应用层雪花算法生成；迁移只初始化必要配置，不生成演示任务数据。

CREATE TABLE edu_teacher_class (
    id BIGINT NOT NULL,
    teacher_user_id BIGINT NOT NULL,
    class_organization_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_to TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_edu_teacher_class_pair UNIQUE (teacher_user_id, class_organization_id),
    CONSTRAINT fk_edu_teacher_class_teacher FOREIGN KEY (teacher_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_edu_teacher_class_class FOREIGN KEY (class_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT ck_edu_teacher_class_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_edu_teacher_class_teacher_status
    ON edu_teacher_class (teacher_user_id, status);
CREATE INDEX idx_edu_teacher_class_class_status
    ON edu_teacher_class (class_organization_id, status);

CREATE TABLE learn_task (
    id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_organization_id BIGINT NULL,
    creator_user_id BIGINT NOT NULL,
    title VARCHAR(50) NOT NULL,
    difficulty_level INT NOT NULL,
    base_points INT NOT NULL,
    duration_minutes INT NOT NULL,
    scheduled_date DATE NOT NULL,
    category_code VARCHAR(64) NULL,
    remark VARCHAR(200) NULL,
    reviewer_user_id BIGINT NOT NULL,
    review_timeout_hours INT NOT NULL DEFAULT 72,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_learn_task_source_organization FOREIGN KEY (source_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_learn_task_creator FOREIGN KEY (creator_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_learn_task_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user (id),
    CONSTRAINT ck_learn_task_source_type CHECK (source_type IN ('FAMILY', 'ORGANIZATION', 'TEACHER')),
    CONSTRAINT ck_learn_task_difficulty CHECK (difficulty_level IN (1, 2, 3)),
    CONSTRAINT ck_learn_task_base_points CHECK (base_points IN (10, 20, 30)),
    CONSTRAINT ck_learn_task_duration CHECK (duration_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_learn_task_review_timeout CHECK (review_timeout_hours > 0),
    CONSTRAINT ck_learn_task_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE INDEX idx_learn_task_creator_status_date
    ON learn_task (creator_user_id, status, scheduled_date);
CREATE INDEX idx_learn_task_source_status
    ON learn_task (source_organization_id, source_type, status);
CREATE INDEX idx_learn_task_published_at
    ON learn_task (published_at);

CREATE TABLE learn_task_target (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    target_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_learn_task_target UNIQUE (task_id, target_type, target_id),
    CONSTRAINT fk_learn_task_target_task FOREIGN KEY (task_id) REFERENCES learn_task (id),
    CONSTRAINT ck_learn_task_target_type CHECK (target_type IN ('ORGANIZATION', 'STUDENT'))
);

CREATE INDEX idx_learn_task_target_task ON learn_task_target (task_id);

CREATE TABLE learn_task_tag (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    tag_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_learn_task_tag UNIQUE (task_id, tag_code),
    CONSTRAINT fk_learn_task_tag_task FOREIGN KEY (task_id) REFERENCES learn_task (id)
);

CREATE INDEX idx_learn_task_tag_task ON learn_task_tag (task_id);

CREATE TABLE learn_task_assignment (
    id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    source_organization_id BIGINT NULL,
    current_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CLAIM',
    current_reviewer_id BIGINT NOT NULL,
    scheduled_date DATE NOT NULL,
    due_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_learn_task_assignment_task_student UNIQUE (task_id, student_id),
    CONSTRAINT fk_learn_task_assignment_task FOREIGN KEY (task_id) REFERENCES learn_task (id),
    CONSTRAINT fk_learn_task_assignment_student FOREIGN KEY (student_id) REFERENCES edu_student (id),
    CONSTRAINT fk_learn_task_assignment_source_organization FOREIGN KEY (source_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_learn_task_assignment_reviewer FOREIGN KEY (current_reviewer_id) REFERENCES sys_user (id),
    CONSTRAINT ck_learn_task_assignment_source_type CHECK (source_type IN ('FAMILY', 'ORGANIZATION', 'TEACHER')),
    CONSTRAINT ck_learn_task_assignment_status CHECK (current_status IN ('PENDING_CLAIM'))
);

CREATE INDEX idx_learn_task_assignment_student_status_date
    ON learn_task_assignment (student_id, current_status, scheduled_date);
CREATE INDEX idx_learn_task_assignment_reviewer_status
    ON learn_task_assignment (current_reviewer_id, current_status);
CREATE INDEX idx_learn_task_assignment_source_status
    ON learn_task_assignment (source_organization_id, current_status);

INSERT INTO sys_dictionary_type (
    id, type_code, type_name, status, sort_order
) VALUES
    (1874244142494646342, 'TASK_CATEGORY', '任务分类', 'ENABLED', 100),
    (1874244142494646343, 'TASK_TAG', '任务标签', 'ENABLED', 110);

INSERT INTO sys_dictionary_item (
    id, type_id, item_code, item_name, sort_order, is_default, status
) VALUES
    (1874244142494646344, 1874244142494646342, 'GENERAL', '通用任务', 10, 1, 'ENABLED'),
    (1874244142494646345, 1874244142494646343, 'DAILY', '日常', 10, 0, 'ENABLED');

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES
    (1874244142494646346, 'LEARNING_TASK_MANAGEMENT', '学习任务管理', 'GLOBAL', 'GLOBAL', 'ENABLED', 1,
        '控制任务草稿、发布、班级关系、任务候选项和学生任务读取能力。');

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description
) VALUES
    (1874244142494646347, 'STUDENT_CLASS_ASSIGN', '配置学生当前班级', 'OPERATION', 'WEB', NULL, 70, 'ENABLED',
        '机构管理员在授权组织子树内配置学生当前班级。'),
    (1874244142494646348, 'TEACHER_CLASS_ASSIGN', '配置教师班级', 'OPERATION', 'WEB', NULL, 80, 'ENABLED',
        '机构管理员在授权组织子树内配置教师班级关系。'),
    (1874244142494646349, 'LEARNING_TASK_CREATE', '创建学习任务', 'OPERATION', 'WEB', NULL, 90, 'ENABLED',
        '家长、机构管理员或教师在各自数据范围内创建任务草稿。'),
    (1874244142494646350, 'LEARNING_TASK_READ_MANAGED', '查询可管理学习任务', 'OPERATION', 'WEB', NULL, 100, 'ENABLED',
        '家长、机构管理员或教师查询各自可管理的任务。'),
    (1874244142494646351, 'LEARNING_TASK_PUBLISH', '发布学习任务', 'OPERATION', 'WEB', NULL, 110, 'ENABLED',
        '家长、机构管理员或教师发布各自可管理的任务。'),
    (1874244142494646352, 'TASK_ASSIGNMENT_READ_SELF', '查询本人学习任务', 'OPERATION', 'MINIAPP', NULL, 120, 'ENABLED',
        '学生在小程序查询本人任务实例。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646353, 1874244142494646275, 1874244142494646347),
    (1874244142494646354, 1874244142494646275, 1874244142494646348),
    (1874244142494646355, 1874244142494646275, 1874244142494646349),
    (1874244142494646356, 1874244142494646275, 1874244142494646350),
    (1874244142494646357, 1874244142494646275, 1874244142494646351),
    (1874244142494646358, 1874244142494646277, 1874244142494646349),
    (1874244142494646359, 1874244142494646277, 1874244142494646350),
    (1874244142494646360, 1874244142494646277, 1874244142494646351),
    (1874244142494646361, 1874244142494646276, 1874244142494646349),
    (1874244142494646362, 1874244142494646276, 1874244142494646350),
    (1874244142494646363, 1874244142494646276, 1874244142494646351),
    (1874244142494646364, 1874244142494646278, 1874244142494646352);
