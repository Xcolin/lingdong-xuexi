-- Fresh demo identities and valid organization scopes for the canonical authorization model.
INSERT INTO edu_teacher_profile(user_id,status) VALUES (2190000000000010004,'ENABLED'),(2190000000000010005,'ENABLED');
UPDATE sys_user_role SET organization_id=2190000000000020003,organization_scope_key='ORG:2190000000000020003' WHERE user_id=2190000000000010004 AND role_id=(SELECT id FROM sys_role WHERE role_code='TEACHER');
UPDATE sys_user_role SET organization_id=2190000000000020004,organization_scope_key='ORG:2190000000000020004' WHERE user_id=2190000000000010005 AND role_id=(SELECT id FROM sys_role WHERE role_code='TEACHER');
INSERT INTO sys_organization(id,parent_id,parent_scope_key,organization_code,organization_name,organization_type,organization_path,sort_order,status,effective_status,version_no) VALUES
(2190000000000020011,NULL,'ROOT','BANSUI_DEMO_FAMILY1','示例家庭一','FAMILY','/BANSUI_DEMO_FAMILY1/',11,'ENABLED','ENABLED',1),
(2190000000000020012,NULL,'ROOT','BANSUI_DEMO_FAMILY2','示例家庭二','FAMILY','/BANSUI_DEMO_FAMILY2/',12,'ENABLED','ENABLED',1);
INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES
(2190000000000050011,2190000000000010006,2190000000000020011),
(2190000000000050012,2190000000000010007,2190000000000020012);
INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES(2190000000000060011,2190000000000020011,2190000000000010006);
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key)
SELECT 2190000000000040011,2190000000000010006,id,2190000000000020011,'ORG:2190000000000020011' FROM sys_role WHERE role_code='PARENT_ADMIN';
