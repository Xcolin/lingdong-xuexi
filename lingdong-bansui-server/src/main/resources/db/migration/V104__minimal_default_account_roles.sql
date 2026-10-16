ALTER TABLE sys_user_role ADD COLUMN automatic_organization_admin TINYINT NOT NULL DEFAULT 0;
-- New account identities carry SELF only and no business permission/menu grants.
INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status,description)
SELECT 1874244142494646281,'ORG_MEMBER','普通机构成员','BUILT_IN','SELF',1,'ENABLED','普通机构账号基础身份，仅本人数据范围，不含业务管理权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='ORG_MEMBER');
INSERT INTO sys_role(id,role_code,role_name,role_type,data_scope,built_in,status,description)
SELECT 1874244142494646282,'PLATFORM_USER','普通平台用户','BUILT_IN','SELF',1,'ENABLED','普通平台账号基础身份，仅本人数据范围，不含业务管理权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='PLATFORM_USER');
