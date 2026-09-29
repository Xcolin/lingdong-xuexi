-- V48：机构管理员班级基础管理、双端教师绑定和班级停用任务失效。
-- 不新增业务表；所有新增基础数据标识均为 19 位雪花数字。

ALTER TABLE learn_task_assignment
    DROP CONSTRAINT ck_learn_task_assignment_status;

ALTER TABLE learn_task_assignment
    ADD CONSTRAINT ck_learn_task_assignment_status CHECK (
        current_status IN ('PENDING_CLAIM', 'IN_PROGRESS', 'PENDING_REVIEW',
            'NEEDS_IMPROVEMENT', 'EXEMPT', 'COMPLETED', 'INVALIDATED')
    );

ALTER TABLE learn_task_assignment_event
    DROP CONSTRAINT ck_task_assignment_event_type;

ALTER TABLE learn_task_assignment_event
    ADD CONSTRAINT ck_task_assignment_event_type CHECK (
        event_type IN ('CLAIMED', 'PAUSED', 'RESUMED', 'ABANDONED', 'CHECKED_IN',
            'REVIEW_REJECTED', 'REVIEW_APPROVED', 'REVIEWER_TRANSFERRED', 'EXEMPTED',
            'POINT_CORRECTED', 'MARKED_NEEDS_IMPROVEMENT', 'CLASS_INVALIDATED')
    );

ALTER TABLE sys_organization_change_audit
    DROP CONSTRAINT ck_sys_organization_change_audit_event;

ALTER TABLE sys_organization_change_audit
    ADD CONSTRAINT ck_sys_organization_change_audit_event CHECK (
        event_type IN ('CREATE', 'DIRECT_UPDATE', 'ENABLE', 'DISABLE',
            'REQUEST', 'APPLY', 'REJECT', 'EXECUTION_FAILED')
    );

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in, description
) VALUES (
    1874244142494646571, 'CLASS_MANAGEMENT', '班级管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '控制机构管理员在 Web 与小程序查询、新增、编辑和启停授权学校下的班级。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646572, 'CLASS_READ', '查询班级',
     'OPERATION', 'BOTH', NULL, 80, 'ENABLED', '查询机构管理员授权范围内的班级。'),
    (1874244142494646573, 'CLASS_CREATE', '新增班级',
     'OPERATION', 'BOTH', NULL, 90, 'ENABLED', '在授权且有效的学校下新增班级。'),
    (1874244142494646574, 'CLASS_UPDATE', '编辑班级',
     'OPERATION', 'BOTH', NULL, 100, 'ENABLED', '编辑授权范围内班级的名称和排序。'),
    (1874244142494646575, 'CLASS_STATUS_CHANGE', '启停班级',
     'OPERATION', 'BOTH', NULL, 110, 'ENABLED', '启用或停用授权范围内的班级。');

INSERT INTO sys_role_permission (id, role_id, permission_id) VALUES
    (1874244142494646576, 1874244142494646275, 1874244142494646572),
    (1874244142494646577, 1874244142494646275, 1874244142494646573),
    (1874244142494646578, 1874244142494646275, 1874244142494646574),
    (1874244142494646579, 1874244142494646275, 1874244142494646575);

UPDATE sys_permission
SET client_type = 'BOTH'
WHERE permission_code = 'TEACHER_CLASS_ASSIGN';
