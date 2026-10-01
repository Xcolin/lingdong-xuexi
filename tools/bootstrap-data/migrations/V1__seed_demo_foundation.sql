-- 仅供显式选择的联调环境使用；正常应用启动不会加载此目录。
-- 密码仅通过本地随机生成的 BCrypt 占位符注入。已有权限和功能开关保持原有分权。
INSERT INTO sys_user (id,username,display_name,user_type,password_hash,status) VALUES
(2190000000000010001,'admin','系统管理员（联调）','PLATFORM','${adminHash}','ENABLED'),
(2190000000000010002,'auditor','系统审核员（联调）','PLATFORM','${auditorHash}','ENABLED'),
(2190000000000010003,'orgadmin','示例学校管理员','ORGANIZATION','${orgadminHash}','ENABLED'),
(2190000000000010004,'teacher01','示例教师一','ORGANIZATION','${teacher01Hash}','ENABLED'),
(2190000000000010005,'teacher02','示例教师二','ORGANIZATION','${teacher02Hash}','ENABLED'),
(2190000000000010006,'parent01','示例家长一','FAMILY','${parent01Hash}','ENABLED'),
(2190000000000010007,'parent02','示例家长二','FAMILY','${parent02Hash}','ENABLED');
INSERT INTO sys_organization(id,parent_id,parent_scope_key,organization_code,organization_name,organization_type,organization_path,sort_order,status,effective_status,version_no) VALUES
(2190000000000020001,NULL,'ROOT','BANSUI_DEMO_SCHOOL','灵动伴随示例学校','SCHOOL','/BANSUI_DEMO_SCHOOL/',1,'ENABLED','ENABLED',1),
(2190000000000020002,2190000000000020001,'PARENT:2190000000000020001','BANSUI_DEMO_GRADE1','一年级（联调）','GRADE','/BANSUI_DEMO_SCHOOL/BANSUI_DEMO_GRADE1/',2,'ENABLED','ENABLED',1),
(2190000000000020003,2190000000000020002,'PARENT:2190000000000020002','BANSUI_DEMO_CLASS1','一年级一班（联调）','CLASS','/BANSUI_DEMO_SCHOOL/BANSUI_DEMO_GRADE1/BANSUI_DEMO_CLASS1/',3,'ENABLED','ENABLED',1),
(2190000000000020004,2190000000000020002,'PARENT:2190000000000020002','BANSUI_DEMO_CLASS2','一年级二班（联调）','CLASS','/BANSUI_DEMO_SCHOOL/BANSUI_DEMO_GRADE1/BANSUI_DEMO_CLASS2/',4,'ENABLED','ENABLED',1);
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040001,2190000000000010001,id,NULL,'GLOBAL' FROM sys_role WHERE role_code='SYS_ADMIN' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040002,2190000000000010002,id,NULL,'GLOBAL' FROM sys_role WHERE role_code='SYS_AUDITOR' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040003,2190000000000010003,id,2190000000000020001,'ORG:2190000000000020001' FROM sys_role WHERE role_code='ORG_ADMIN' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040004,2190000000000010004,id,2190000000000020001,'ORG:2190000000000020001' FROM sys_role WHERE role_code='TEACHER' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040005,2190000000000010005,id,2190000000000020001,'ORG:2190000000000020001' FROM sys_role WHERE role_code='TEACHER' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040006,2190000000000010006,id,NULL,'GLOBAL' FROM sys_role WHERE role_code='PARENT' AND status='ENABLED';
INSERT INTO sys_user_role(id,user_id,role_id,organization_id,organization_scope_key) SELECT 2190000000000040007,2190000000000010007,id,NULL,'GLOBAL' FROM sys_role WHERE role_code='PARENT' AND status='ENABLED';
INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(2190000000000050003,2190000000000010003,2190000000000020001);
INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(2190000000000050004,2190000000000010004,2190000000000020001);
INSERT INTO sys_user_organization(id,user_id,organization_id) VALUES(2190000000000050005,2190000000000010005,2190000000000020001);
INSERT INTO sys_organization_admin(id,organization_id,user_id) VALUES(2190000000000060001,2190000000000020001,2190000000000010003);
INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(2190000000000070001,2190000000000010004,2190000000000020003,'ACTIVE');
INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) VALUES(2190000000000080001,2190000000000010006,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO edu_student(id,student_name,grade_code,status) VALUES(2190000000000030001,'示例学生一',NULL,'ENABLED');
INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(2190000000000090001,2190000000000010006,2190000000000030001,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY');
INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status) VALUES(2190000000000100001,2190000000000030001,2190000000000020001,'ENROLLMENT','ACTIVE'),(2190000000000100002,2190000000000030001,2190000000000020003,'CLASS','ACTIVE');
INSERT INTO growth_point_account(id,student_id,total_points,available_points,version_no) VALUES(2190000000000030001,2190000000000030001,0,0,0);
INSERT INTO growth_point_dormancy_state(id,student_id,last_activity_at,reminder_due_at,clear_due_at,version_no) VALUES(2190000000000030001,2190000000000030001,CURRENT_TIMESTAMP,TIMESTAMPADD(DAY,27,CURRENT_TIMESTAMP),TIMESTAMPADD(DAY,30,CURRENT_TIMESTAMP),0);
INSERT INTO edu_teacher_class(id,teacher_user_id,class_organization_id,status) VALUES(2190000000000070002,2190000000000010005,2190000000000020004,'ACTIVE');
INSERT INTO auth_parent_profile(id,user_id,onboarding_status,first_login_at,onboarding_completed_at) VALUES(2190000000000080002,2190000000000010007,'COMPLETED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP);
INSERT INTO edu_student(id,student_name,grade_code,status) VALUES(2190000000000030002,'示例学生二',NULL,'ENABLED');
INSERT INTO edu_parent_student(id,parent_user_id,student_id,relation_role,status,primary_scope_key) VALUES(2190000000000090002,2190000000000010007,2190000000000030002,'PRIMARY_GUARDIAN','ACTIVE','PRIMARY');
INSERT INTO edu_student_organization(id,student_id,organization_id,relation_type,status) VALUES(2190000000000100003,2190000000000030002,2190000000000020001,'ENROLLMENT','ACTIVE'),(2190000000000100004,2190000000000030002,2190000000000020004,'CLASS','ACTIVE');
INSERT INTO growth_point_account(id,student_id,total_points,available_points,version_no) VALUES(2190000000000030002,2190000000000030002,0,0,0);
INSERT INTO growth_point_dormancy_state(id,student_id,last_activity_at,reminder_due_at,clear_due_at,version_no) VALUES(2190000000000030002,2190000000000030002,CURRENT_TIMESTAMP,TIMESTAMPADD(DAY,27,CURRENT_TIMESTAMP),TIMESTAMPADD(DAY,30,CURRENT_TIMESTAMP),0);
