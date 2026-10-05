-- 管理端权限与组织体系升级（菜单侧）：编码-权限统一。
-- 原则：页面与权限按钮的编码即权限编码；纯 UI 按钮编码保持不变、不参与授权。
-- 授权基线不变原则：回填仅做最小化授权保持（见第 6 段），绝不把既有写操作权限
-- 授予原本不拥有它的角色，保持 246 处 @RequirePermission 运行时判定基线零改动；
-- 无授权角色的菜单可见性按权限收敛，后续由管理员在菜单树授权视图中按需授予。
-- 例外：EXPORT_JOB_CREATE 遵循 V57 最小导出权限原则不回填。

-- 1. 菜单可授权标记（PAGE 与权限按钮使用）
ALTER TABLE sys_menu ADD COLUMN grantable TINYINT NOT NULL DEFAULT 0;

-- 2. 补充菜单树授权所需的新权限编码（此前无对应权限的页面/按钮）
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649101, 'DASHBOARD_READ', '查看工作台', 'PAGE', 'WEB', NULL, 300, 'ENABLED', '管理端工作台页面读取权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'DASHBOARD_READ');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649102, 'GROWTH_POINT_LEDGER_READ', '查看积分台账', 'PAGE', 'WEB', NULL, 305, 'ENABLED', '管理端积分台账页面读取权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'GROWTH_POINT_LEDGER_READ');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649103, 'GROWTH_REVIEW_MANAGE', '管理成长复盘', 'PAGE', 'WEB', NULL, 310, 'ENABLED', '管理端成长复盘页面及补录权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'GROWTH_REVIEW_MANAGE');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649104, 'GROWTH_REVIEW_PDF_EXPORT', '导出成长复盘PDF', 'BUTTON', 'WEB', NULL, 315, 'ENABLED', '成长复盘 PDF 导出按钮权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'GROWTH_REVIEW_PDF_EXPORT');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649105, 'REWARD_MANAGE', '管理奖励', 'PAGE', 'WEB', NULL, 320, 'ENABLED', '管理端奖励管理页面权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'REWARD_MANAGE');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649106, 'REWARD_EXCHANGE_REVIEW', '审核奖励兑换', 'BUTTON', 'WEB', NULL, 325, 'ENABLED', '管理端奖励兑换审批按钮权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'REWARD_EXCHANGE_REVIEW');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649107, 'ANONYMOUS_RANK_PREFERENCE_MANAGE', '管理排行查看授权', 'PAGE', 'WEB', NULL, 330, 'ENABLED', '管理端排行查看授权页面权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'ANONYMOUS_RANK_PREFERENCE_MANAGE');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649108, 'STUDENT_LOGIN_READ', '查看学生登录管理', 'PAGE', 'WEB', NULL, 335, 'ENABLED', '管理端学生登录管理页面读取权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'STUDENT_LOGIN_READ');
-- V38 种子为主家长解绑/转移使用了旧编码（PRIMARY_PARENT_UNBIND/PRIMARY_PARENT_TRANSFER），
-- 后端控制器实际校验编码为 PRIMARY_PARENT_SELF_UNBIND/PRIMARY_PARENT_TRANSFER_CREATE，此处补齐种子。
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649109, 'PRIMARY_PARENT_SELF_UNBIND', '主家长自助解绑', 'BUTTON', 'WEB', NULL, 340, 'ENABLED', '主家长解绑亲子关系按钮权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'PRIMARY_PARENT_SELF_UNBIND');
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, client_type, parent_id, sort_order, status, description)
SELECT 1874244142494649110, 'PRIMARY_PARENT_TRANSFER_CREATE', '发起主家长转移', 'BUTTON', 'WEB', NULL, 345, 'ENABLED', '主家长转移发起按钮权限，由菜单管理同步维护。'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'PRIMARY_PARENT_TRANSFER_CREATE');

-- 3. 页面菜单重编码：编码对齐该页面的读取权限编码，并开放可授权
UPDATE sys_menu SET code = 'DASHBOARD_READ', permission_code = 'DASHBOARD_READ', grantable = 1 WHERE code = 'dashboard' AND type = 'PAGE';
UPDATE sys_menu SET code = 'SYSTEM_TASK_READ', permission_code = 'SYSTEM_TASK_READ', grantable = 1 WHERE code = 'system-tasks' AND type = 'PAGE';
UPDATE sys_menu SET code = 'FEATURE_TOGGLE_READ', permission_code = 'FEATURE_TOGGLE_READ', grantable = 1 WHERE code = 'feature-management' AND type = 'PAGE';
UPDATE sys_menu SET code = 'ATTENDANCE_READ', permission_code = 'ATTENDANCE_READ', grantable = 1 WHERE code = 'attendance-records' AND type = 'PAGE';
UPDATE sys_menu SET code = 'LEARNING_TASK_READ_MANAGED', permission_code = 'LEARNING_TASK_READ_MANAGED', grantable = 1 WHERE code = 'learning-tasks' AND type = 'PAGE';
UPDATE sys_menu SET code = 'EXCEPTION_REPORT_READ', permission_code = 'EXCEPTION_REPORT_READ', grantable = 1 WHERE code = 'exception-reports' AND type = 'PAGE';
UPDATE sys_menu SET code = 'GROWTH_POINT_LEDGER_READ', permission_code = 'GROWTH_POINT_LEDGER_READ', grantable = 1 WHERE code = 'growth-points' AND type = 'PAGE';
UPDATE sys_menu SET code = 'REWARD_MANAGE', permission_code = 'REWARD_MANAGE', grantable = 1 WHERE code = 'rewards' AND type = 'PAGE';
UPDATE sys_menu SET code = 'GROWTH_REVIEW_MANAGE', permission_code = 'GROWTH_REVIEW_MANAGE', grantable = 1 WHERE code = 'growth-reviews' AND type = 'PAGE';
UPDATE sys_menu SET code = 'ANONYMOUS_CLASS_RANK_READ', permission_code = 'ANONYMOUS_CLASS_RANK_READ', grantable = 1 WHERE code = 'anonymous-ranks' AND type = 'PAGE';
UPDATE sys_menu SET code = 'ANONYMOUS_RANK_PREFERENCE_MANAGE', permission_code = 'ANONYMOUS_RANK_PREFERENCE_MANAGE', grantable = 1 WHERE code = 'rank-preferences' AND type = 'PAGE';
UPDATE sys_menu SET code = 'STUDENT_LOGIN_READ', permission_code = 'STUDENT_LOGIN_READ', grantable = 1 WHERE code = 'student-login' AND type = 'PAGE';
UPDATE sys_menu SET code = 'PARENT_RELATIONSHIP_READ', permission_code = 'PARENT_RELATIONSHIP_READ', grantable = 1 WHERE code = 'parent-relationships' AND type = 'PAGE';
UPDATE sys_menu SET code = 'TEACHER_READ', permission_code = 'TEACHER_READ', grantable = 1 WHERE code = 'teachers' AND type = 'PAGE';
UPDATE sys_menu SET code = 'IAM_USER_READ', permission_code = 'IAM_USER_READ', grantable = 1 WHERE code = 'users' AND type = 'PAGE';
UPDATE sys_menu SET code = 'IAM_ROLE_READ', permission_code = 'IAM_ROLE_READ', grantable = 1 WHERE code = 'iam' AND type = 'PAGE';
UPDATE sys_menu SET code = 'DICTIONARY_READ', permission_code = 'DICTIONARY_READ', grantable = 1 WHERE code = 'dictionaries' AND type = 'PAGE';
UPDATE sys_menu SET code = 'CACHE_READ', permission_code = 'CACHE_READ', grantable = 1 WHERE code = 'cache-management' AND type = 'PAGE';
UPDATE sys_menu SET code = 'INTERFACE_SERVICE_READ', permission_code = 'INTERFACE_SERVICE_READ', grantable = 1 WHERE code = 'interface-services' AND type = 'PAGE';
UPDATE sys_menu SET code = 'ATTACHMENT_READ', permission_code = 'ATTACHMENT_READ', grantable = 1 WHERE code = 'attachment-management' AND type = 'PAGE';
UPDATE sys_menu SET code = 'IMPORT_EXPORT_TEMPLATE_READ', permission_code = 'IMPORT_EXPORT_TEMPLATE_READ', grantable = 1 WHERE code = 'import-export-templates' AND type = 'PAGE';
UPDATE sys_menu SET code = 'IMPORT_JOB_READ', permission_code = 'IMPORT_JOB_READ', grantable = 1 WHERE code = 'import-jobs' AND type = 'PAGE';
UPDATE sys_menu SET code = 'EXPORT_JOB_READ', permission_code = 'EXPORT_JOB_READ', grantable = 1 WHERE code = 'export-jobs' AND type = 'PAGE';
UPDATE sys_menu SET code = 'ORG_NODE_READ', permission_code = 'ORG_NODE_READ', grantable = 1 WHERE code = 'organizations' AND type = 'PAGE';
UPDATE sys_menu SET code = 'MENU_READ', permission_code = 'MENU_READ', grantable = 1 WHERE code = 'menu-management' AND type = 'PAGE';

-- 4. 权限型按钮重编码：编码对齐既有后端权限编码（每个权限编码仅映射一个主按钮），其余按钮保持纯 UI 定位
UPDATE sys_menu SET code = 'ATTENDANCE_RECORD', permission_code = 'ATTENDANCE_RECORD', grantable = 1 WHERE code = 'attendance-records.attendance-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'ATTACHMENT_RULE_MANAGE', permission_code = 'ATTACHMENT_RULE_MANAGE', grantable = 1 WHERE code = 'attachment-management.attachment-management-page.6' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'CACHE_MANAGE', permission_code = 'CACHE_MANAGE', grantable = 1 WHERE code = 'cache-management.cache-management-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'CACHE_REVIEW', permission_code = 'CACHE_REVIEW', grantable = 1 WHERE code = 'cache-management.cache-management-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'DICTIONARY_MANAGE', permission_code = 'DICTIONARY_MANAGE', grantable = 1 WHERE code = 'dictionaries.dictionary-management-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'EXCEPTION_REPORT_CREATE', permission_code = 'EXCEPTION_REPORT_CREATE', grantable = 1 WHERE code = 'exception-reports.exception-report-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'EXCEPTION_REPORT_HANDLE', permission_code = 'EXCEPTION_REPORT_HANDLE', grantable = 1 WHERE code = 'exception-reports.exception-report-page.7' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'EXPORT_JOB_CREATE', permission_code = 'EXPORT_JOB_CREATE', grantable = 1 WHERE code = 'export-jobs.export-job-management-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'FEATURE_TOGGLE_MANAGE', permission_code = 'FEATURE_TOGGLE_MANAGE', grantable = 1 WHERE code = 'feature-management.feature-management-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'FEATURE_TOGGLE_REVIEW', permission_code = 'FEATURE_TOGGLE_REVIEW', grantable = 1 WHERE code = 'feature-management.feature-management-page.5' AND type = 'BUTTON';
-- 成长积分台账导出走导出中心（EXPORT_JOB_CREATE），按钮保持纯 UI 定位，不映射独立权限编码
UPDATE sys_menu SET code = 'GROWTH_REVIEW_PDF_EXPORT', permission_code = 'GROWTH_REVIEW_PDF_EXPORT', grantable = 1 WHERE code = 'growth-reviews.create-growth-review-export.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_ROLE_CREATE', permission_code = 'IAM_ROLE_CREATE', grantable = 1 WHERE code = 'iam.iam-management-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_PERMISSION_CREATE', permission_code = 'IAM_PERMISSION_CREATE', grantable = 1 WHERE code = 'iam.iam-management-page.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_ROLE_PERMISSION_GRANT', permission_code = 'IAM_ROLE_PERMISSION_GRANT', grantable = 1 WHERE code = 'iam.iam-management-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_USER_PERMISSION_CONFIGURE', permission_code = 'IAM_USER_PERMISSION_CONFIGURE', grantable = 1 WHERE code = 'iam.iam-management-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_AUDIT_READ', permission_code = 'IAM_AUDIT_READ', grantable = 1 WHERE code = 'iam.iam-audit-panel.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IMPORT_EXPORT_TEMPLATE_MANAGE', permission_code = 'IMPORT_EXPORT_TEMPLATE_MANAGE', grantable = 1 WHERE code = 'import-export-templates.import-export-template-management-page.5' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IMPORT_JOB_CREATE', permission_code = 'IMPORT_JOB_CREATE', grantable = 1 WHERE code = 'import-jobs.import-job-management-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'STUDENT_IMPORT_EXECUTE', permission_code = 'STUDENT_IMPORT_EXECUTE', grantable = 1 WHERE code = 'import-jobs.import-job-management-page.8' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'INTERFACE_SERVICE_MANAGE', permission_code = 'INTERFACE_SERVICE_MANAGE', grantable = 1 WHERE code = 'interface-services.interface-service-management-page.6' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'INTERFACE_SERVICE_REVIEW', permission_code = 'INTERFACE_SERVICE_REVIEW', grantable = 1 WHERE code = 'interface-services.interface-service-management-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'LEARNING_TASK_CREATE', permission_code = 'LEARNING_TASK_CREATE', grantable = 1 WHERE code = 'learning-tasks.learning-task-management-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'LEARNING_TASK_PUBLISH', permission_code = 'LEARNING_TASK_PUBLISH', grantable = 1 WHERE code = 'learning-tasks.publish' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'LEARNING_TASK_COPY_PREVIOUS_DAY', permission_code = 'LEARNING_TASK_COPY_PREVIOUS_DAY', grantable = 1 WHERE code = 'learning-tasks.previous-day-task-copy-modal.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TASK_ASSIGNMENT_REVIEW', permission_code = 'TASK_ASSIGNMENT_REVIEW', grantable = 1 WHERE code = 'learning-tasks.task-review-drawer.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TASK_ASSIGNMENT_DEFER', permission_code = 'TASK_ASSIGNMENT_DEFER', grantable = 1 WHERE code = 'learning-tasks.task-defer-queue.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL', permission_code = 'LEARNING_TASK_TEMPLATE_MANAGE_PERSONAL', grantable = 1 WHERE code = 'learning-tasks.task-template-editor-modal.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'MENU_MANAGE', permission_code = 'MENU_MANAGE', grantable = 1 WHERE code = 'menu-management.menu-management-page.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'CLASS_CREATE', permission_code = 'CLASS_CREATE', grantable = 1 WHERE code = 'organizations.class-management-panel.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'ORG_NODE_CHANGE_REVIEW', permission_code = 'ORG_NODE_CHANGE_REVIEW', grantable = 1 WHERE code = 'organizations.organization-change-review-panel.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'ORG_NODE_UPDATE', permission_code = 'ORG_NODE_UPDATE', grantable = 1 WHERE code = 'organizations.organization-node-editor-drawer.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'STUDENT_CLASS_ASSIGN', permission_code = 'STUDENT_CLASS_ASSIGN', grantable = 1 WHERE code = 'organizations.student-class-assignment-drawer.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'PARENT_ACCOUNT_LIFECYCLE_MANAGE', permission_code = 'PARENT_ACCOUNT_LIFECYCLE_MANAGE', grantable = 1 WHERE code = 'organizations.parent-mobile-manual-recovery-drawer.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'SECONDARY_PARENT_INVITE_CREATE', permission_code = 'SECONDARY_PARENT_INVITE_CREATE', grantable = 1 WHERE code = 'parent-relationships.parent-relationship-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'PRIMARY_PARENT_TRANSFER_CREATE', permission_code = 'PRIMARY_PARENT_TRANSFER_CREATE', grantable = 1 WHERE code = 'parent-relationships.parent-relationship-page.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'PRIMARY_PARENT_SELF_UNBIND', permission_code = 'PRIMARY_PARENT_SELF_UNBIND', grantable = 1 WHERE code = 'parent-relationships.parent-relationship-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'SECONDARY_PARENT_UNBIND', permission_code = 'SECONDARY_PARENT_UNBIND', grantable = 1 WHERE code = 'parent-relationships.parent-relationship-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'REWARD_EXCHANGE_REVIEW', permission_code = 'REWARD_EXCHANGE_REVIEW', grantable = 1 WHERE code = 'rewards.reward-management-page.7' AND type = 'BUTTON';
-- 排行查看授权撤回按钮与页面共用权限语义，页面已映射，按钮保持纯 UI 定位（编码唯一约束）
UPDATE sys_menu SET code = 'STUDENT_LOGIN_QR_CREATE', permission_code = 'STUDENT_LOGIN_QR_CREATE', grantable = 1 WHERE code = 'student-login.student-login-management-page.3' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'STUDENT_WECHAT_UNBIND', permission_code = 'STUDENT_WECHAT_UNBIND', grantable = 1 WHERE code = 'student-login.student-login-management-page.4' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TEACHER_BATCH_MANAGE', permission_code = 'TEACHER_BATCH_MANAGE', grantable = 1 WHERE code = 'teachers.teacher-management-page.1' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TEACHER_CREATE', permission_code = 'TEACHER_CREATE', grantable = 1 WHERE code = 'teachers.teacher-management-page.2' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TEACHER_UPDATE', permission_code = 'TEACHER_UPDATE', grantable = 1 WHERE code = 'teachers.profile.edit' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TEACHER_PASSWORD_RESET', permission_code = 'TEACHER_PASSWORD_RESET', grantable = 1 WHERE code = 'teachers.password.reset' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'TEACHER_CLASS_ASSIGN', permission_code = 'TEACHER_CLASS_ASSIGN', grantable = 1 WHERE code = 'teachers.classes.configure' AND type = 'BUTTON';
UPDATE sys_menu SET code = 'IAM_USER_CREATE', permission_code = 'IAM_USER_CREATE', grantable = 1 WHERE code = 'users.user-management-page.1' AND type = 'BUTTON';

-- 5. 审计约束扩展：组织树直接生效操作（拖拽）的事件与目标类型
ALTER TABLE sys_iam_change_audit DROP CONSTRAINT ck_sys_iam_change_audit_event;
ALTER TABLE sys_iam_change_audit ADD CONSTRAINT ck_sys_iam_change_audit_event CHECK (event_type IN (
 'USER_CREATE','USER_PROFILE_CHANGE','USER_PASSWORD_RESET','USER_STATUS_CHANGE','USER_ORGANIZATION_ASSOCIATE','USER_ROLE_ASSIGN','ROLE_CREATE','PERMISSION_CREATE','ROLE_PERMISSION_CONFIGURE','ROLE_PERMISSION_REMOVE','USER_PERMISSION_CONFIGURE','USER_PERMISSION_REMOVE','ROLE_DATA_SCOPE_ADD','ORGANIZATION_ADMIN_ASSIGN','MENU_CREATE','MENU_UPDATE','MENU_REORDER','ORGANIZATION_TREE_REORDER','ORGANIZATION_TREE_MOVE'
));
ALTER TABLE sys_iam_change_audit DROP CONSTRAINT ck_sys_iam_change_audit_target;
ALTER TABLE sys_iam_change_audit ADD CONSTRAINT ck_sys_iam_change_audit_target CHECK (target_type IN (
 'USER','ROLE','PERMISSION','ROLE_PERMISSION','USER_PERMISSION','ROLE_DATA_SCOPE','ORGANIZATION_ADMIN','USER_ORGANIZATION','USER_ROLE','MENU','ORGANIZATION_TREE'
));

-- 6. 授权保持回填（最小化，不改变既有 API 授权基线）：
--    a) 工作台为管理端核心入口，授予全部启用角色，保持升级前后可登录可用；
--    b) V38 旧码亲子权限的既有拥有者 1:1 平移到对齐后的新码，仅保持不变、不扩大；
--    c) 其余本迁移新增的页面/按钮权限仅授予 SYS_ADMIN，保证系统管理员菜单与按钮可用。
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT 1874244142494649201 + ROW_NUMBER() OVER (ORDER BY r.id), r.id, p.id, 'ALLOW'
FROM sys_role r
JOIN sys_permission p ON p.client_type = 'WEB' AND p.status = 'ENABLED'
WHERE r.status = 'ENABLED'
  AND p.permission_code = 'DASHBOARD_READ'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- b) V38 旧码 → 对齐后新码的既有授权 1:1 平移
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT 1874244142494649301 + ROW_NUMBER() OVER (ORDER BY rp.role_id), rp.role_id, p_new.id, 'ALLOW'
FROM sys_role_permission rp
JOIN sys_permission p_old ON p_old.id = rp.permission_id
  AND p_old.permission_code IN ('PRIMARY_PARENT_UNBIND', 'PRIMARY_PARENT_TRANSFER')
JOIN sys_permission p_new ON p_new.permission_code = CASE p_old.permission_code
  WHEN 'PRIMARY_PARENT_UNBIND' THEN 'PRIMARY_PARENT_SELF_UNBIND'
  ELSE 'PRIMARY_PARENT_TRANSFER_CREATE' END
WHERE NOT EXISTS (
  SELECT 1 FROM sys_role_permission rpn WHERE rpn.role_id = rp.role_id AND rpn.permission_id = p_new.id
);

-- c) 其余新增权限仅授予 SYS_ADMIN
INSERT INTO sys_role_permission (id, role_id, permission_id, effect)
SELECT 1874244142494649401 + ROW_NUMBER() OVER (ORDER BY p.id), r.id, p.id, 'ALLOW'
FROM sys_role r
JOIN sys_permission p ON p.client_type = 'WEB' AND p.status = 'ENABLED'
WHERE r.role_code = 'SYS_ADMIN'
  AND p.permission_code IN (
    'GROWTH_POINT_LEDGER_READ','GROWTH_REVIEW_MANAGE','GROWTH_REVIEW_PDF_EXPORT',
    'REWARD_MANAGE','REWARD_EXCHANGE_REVIEW','ANONYMOUS_RANK_PREFERENCE_MANAGE','STUDENT_LOGIN_READ'
  )
  AND NOT EXISTS (
    SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
