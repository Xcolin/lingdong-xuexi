-- V60：补齐机构管理员与教师双端任务操作、学生进度权限和审核转交查询索引。
-- 全部新增标识为19位雪花数字；不创建自增主键。

UPDATE sys_permission
SET client_type = 'BOTH',
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'LEARNING_TASK_CREATE',
    'LEARNING_TASK_READ_MANAGED',
    'LEARNING_TASK_PUBLISH',
    'TASK_ASSIGNMENT_REVIEW',
    'TASK_ASSIGNMENT_EXEMPT',
    'TASK_ASSIGNMENT_DEFER'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES (
    1874244142494646668, 'LEARNING_TASK_PROGRESS_READ', '查看学习任务学生进度',
    'OPERATION', 'BOTH', NULL, 410, 'ENABLED',
    '机构管理员和教师在授权组织或班级范围内查看机构、教师任务的学生级进度。'
);

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646669, 1874244142494646275, 1874244142494646668, 'ALLOW'),
    (1874244142494646670, 1874244142494646276, 1874244142494646668, 'ALLOW');

CREATE INDEX idx_task_assignment_reviewer_status_source
    ON learn_task_assignment (current_reviewer_id, current_status, source_organization_id);
