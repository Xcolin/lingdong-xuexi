CREATE TABLE sys_menu (
 id BIGINT NOT NULL PRIMARY KEY,
 code VARCHAR(128) NOT NULL UNIQUE,
 name VARCHAR(128) NOT NULL,
 type VARCHAR(16) NOT NULL,
 parent_id BIGINT NULL,
 route VARCHAR(128) NULL,
 icon VARCHAR(64) NULL,
 permission_code VARCHAR(128) NULL,
 sort_order INT NOT NULL DEFAULT 0,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 version BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT fk_sys_menu_parent FOREIGN KEY (parent_id) REFERENCES sys_menu(id),
 CONSTRAINT ck_sys_menu_type CHECK (type IN ('DIRECTORY','PAGE','BUTTON')),
 CONSTRAINT ck_sys_menu_status CHECK (status IN ('ENABLED','DISABLED')),
 CONSTRAINT ck_sys_menu_order CHECK (sort_order >= 0),
 CONSTRAINT ck_sys_menu_route CHECK ((type='PAGE' AND route IS NOT NULL) OR (type<>'PAGE' AND route IS NULL))
);
CREATE INDEX idx_sys_menu_parent_order ON sys_menu(parent_id,sort_order,id);
ALTER TABLE sys_iam_change_audit DROP CONSTRAINT ck_sys_iam_change_audit_event;
ALTER TABLE sys_iam_change_audit ADD CONSTRAINT ck_sys_iam_change_audit_event CHECK (event_type IN (
 'USER_CREATE','USER_PROFILE_CHANGE','USER_PASSWORD_RESET','USER_STATUS_CHANGE','USER_ORGANIZATION_ASSOCIATE','USER_ROLE_ASSIGN','ROLE_CREATE','PERMISSION_CREATE','ROLE_PERMISSION_CONFIGURE','ROLE_PERMISSION_REMOVE','USER_PERMISSION_CONFIGURE','USER_PERMISSION_REMOVE','ROLE_DATA_SCOPE_ADD','ORGANIZATION_ADMIN_ASSIGN','MENU_CREATE','MENU_UPDATE','MENU_REORDER'
));
ALTER TABLE sys_iam_change_audit DROP CONSTRAINT ck_sys_iam_change_audit_target;
ALTER TABLE sys_iam_change_audit ADD CONSTRAINT ck_sys_iam_change_audit_target CHECK (target_type IN (
 'USER','ROLE','PERMISSION','ROLE_PERMISSION','USER_PERMISSION','ROLE_DATA_SCOPE','ORGANIZATION_ADMIN','USER_ORGANIZATION','USER_ROLE','MENU'
));
INSERT INTO sys_permission (id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description) VALUES
 (1874244142494648701,'MENU_READ','查询菜单配置','OPERATION','WEB',NULL,420,'ENABLED','查询管理端菜单及按钮配置'),
 (1874244142494648702,'MENU_MANAGE','维护菜单配置','OPERATION','WEB',NULL,430,'ENABLED','维护菜单与按钮配置及同级顺序');
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
 SELECT 1874244142494648703,id,1874244142494648701,'ALLOW' FROM sys_role WHERE role_code='SYS_ADMIN';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
 SELECT 1874244142494648704,id,1874244142494648702,'ALLOW' FROM sys_role WHERE role_code='SYS_ADMIN';
INSERT INTO sys_role_permission (id,role_id,permission_id,effect)
 SELECT 1874244142494648705,id,1874244142494648701,'ALLOW' FROM sys_role WHERE role_code='SYS_AUDITOR';
