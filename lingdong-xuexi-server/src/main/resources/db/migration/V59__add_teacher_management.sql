-- V59：教师账号维护、班级数据范围、批量管理和班级关系不可变审计。
-- 所有主键和内置数据标识均为19位雪花数字，禁止数据库自增。

CREATE TABLE edu_teacher_class_change_log (
    id BIGINT NOT NULL PRIMARY KEY,
    teacher_user_id BIGINT NOT NULL,
    class_organization_id BIGINT NOT NULL,
    event_type VARCHAR(16) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_teacher_class_change_event
        CHECK (event_type IN ('BIND', 'UNBIND')),
    CONSTRAINT fk_teacher_class_change_teacher
        FOREIGN KEY (teacher_user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_teacher_class_change_class
        FOREIGN KEY (class_organization_id) REFERENCES sys_organization (id),
    CONSTRAINT fk_teacher_class_change_operator
        FOREIGN KEY (operator_user_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_teacher_class_change_teacher_time
    ON edu_teacher_class_change_log (teacher_user_id, created_at);

CREATE INDEX idx_teacher_class_change_class_time
    ON edu_teacher_class_change_log (class_organization_id, created_at);

-- 资料与密码变更沿用统一 IAM 审计；审计值不得保存姓名、手机号或凭证。
ALTER TABLE sys_iam_change_audit
    DROP CONSTRAINT ck_sys_iam_change_audit_event;

ALTER TABLE sys_iam_change_audit
    ADD CONSTRAINT ck_sys_iam_change_audit_event CHECK (event_type IN (
        'USER_CREATE', 'USER_PROFILE_CHANGE', 'USER_PASSWORD_RESET', 'USER_STATUS_CHANGE',
        'USER_ORGANIZATION_ASSOCIATE', 'USER_ROLE_ASSIGN', 'ROLE_CREATE', 'PERMISSION_CREATE',
        'ROLE_PERMISSION_CONFIGURE', 'ROLE_PERMISSION_REMOVE', 'USER_PERMISSION_CONFIGURE',
        'USER_PERMISSION_REMOVE', 'ROLE_DATA_SCOPE_ADD', 'ORGANIZATION_ADMIN_ASSIGN'
    ));

INSERT INTO sys_feature_toggle (
    id, feature_code, feature_name, scope_type, scope_key, status, built_in,
    description
) VALUES (
    1874244142494646655, 'TEACHER_MANAGEMENT', '教师管理',
    'GLOBAL', 'GLOBAL', 'ENABLED', 1,
    '统一控制机构管理员在 Web 与小程序维护授权学校范围内的教师账号和班级关系。'
);

INSERT INTO sys_permission (
    id, permission_code, permission_name, resource_type, client_type,
    parent_id, sort_order, status, description
) VALUES
    (1874244142494646656, 'TEACHER_READ', '查询教师',
     'OPERATION', 'BOTH', NULL, 350, 'ENABLED', '查询授权组织范围内的教师账号和活动班级。'),
    (1874244142494646657, 'TEACHER_CREATE', '新增教师',
     'OPERATION', 'BOTH', NULL, 360, 'ENABLED', '在授权且启用的学校下新增教师账号。'),
    (1874244142494646658, 'TEACHER_UPDATE', '编辑教师',
     'OPERATION', 'BOTH', NULL, 370, 'ENABLED', '编辑授权组织范围内教师的姓名和手机号。'),
    (1874244142494646659, 'TEACHER_STATUS_CHANGE', '变更教师状态',
     'OPERATION', 'BOTH', NULL, 380, 'ENABLED', '启用、停用或锁定授权组织范围内的教师账号。'),
    (1874244142494646660, 'TEACHER_PASSWORD_RESET', '重置教师密码',
     'OPERATION', 'BOTH', NULL, 390, 'ENABLED', '重置授权组织范围内教师的登录密码并撤销活动会话。'),
    (1874244142494646661, 'TEACHER_BATCH_MANAGE', '批量管理教师',
     'OPERATION', 'WEB', NULL, 400, 'ENABLED', '在 Web 端逐项批量启停、锁定、绑定或解绑教师班级。');

INSERT INTO sys_role_permission (id, role_id, permission_id, effect) VALUES
    (1874244142494646662, 1874244142494646275, 1874244142494646656, 'ALLOW'),
    (1874244142494646663, 1874244142494646275, 1874244142494646657, 'ALLOW'),
    (1874244142494646664, 1874244142494646275, 1874244142494646658, 'ALLOW'),
    (1874244142494646665, 1874244142494646275, 1874244142494646659, 'ALLOW'),
    (1874244142494646666, 1874244142494646275, 1874244142494646660, 'ALLOW'),
    (1874244142494646667, 1874244142494646275, 1874244142494646661, 'ALLOW');
