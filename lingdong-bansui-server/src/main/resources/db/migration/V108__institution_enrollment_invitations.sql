-- Explicit class invitations preserve one student identity across institutions.
CREATE TABLE edu_institution_invitation (
 id BIGINT NOT NULL PRIMARY KEY,
 code_hash VARCHAR(64) NOT NULL UNIQUE,
 institution_organization_id BIGINT NOT NULL,
 class_organization_id BIGINT NOT NULL,
 created_by BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 max_uses INTEGER NOT NULL DEFAULT 100,
 used_count INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_inst_inv_institution FOREIGN KEY(institution_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_inst_inv_class FOREIGN KEY(class_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_inst_inv_creator FOREIGN KEY(created_by) REFERENCES sys_user(id),
 CONSTRAINT ck_inst_inv_status CHECK(status IN('ACTIVE','REVOKED')),
 CONSTRAINT ck_inst_inv_uses CHECK(used_count>=0 AND max_uses>0 AND used_count<=max_uses)
);
CREATE TABLE edu_institution_enrollment_request (
 id BIGINT NOT NULL PRIMARY KEY,
 invitation_id BIGINT NOT NULL,
 student_id BIGINT NOT NULL,
 family_organization_id BIGINT NOT NULL,
 institution_organization_id BIGINT NOT NULL,
 class_organization_id BIGINT NOT NULL,
 requested_by BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 reviewed_by BIGINT NULL,
 reviewed_at TIMESTAMP NULL,
 review_reason VARCHAR(200) NULL,
 CONSTRAINT fk_inst_req_invite FOREIGN KEY(invitation_id) REFERENCES edu_institution_invitation(id),
 CONSTRAINT fk_inst_req_student FOREIGN KEY(student_id) REFERENCES edu_student(id),
 CONSTRAINT fk_inst_req_family FOREIGN KEY(family_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_inst_req_institution FOREIGN KEY(institution_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_inst_req_class FOREIGN KEY(class_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_inst_req_actor FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 CONSTRAINT fk_inst_req_reviewer FOREIGN KEY(reviewed_by) REFERENCES sys_user(id),
 CONSTRAINT ck_inst_req_status CHECK(status IN('PENDING','APPROVED','REJECTED'))
);
CREATE INDEX idx_inst_req_class_status ON edu_institution_enrollment_request(class_organization_id,status);
CREATE INDEX idx_inst_req_family_status ON edu_institution_enrollment_request(family_organization_id,status);
CREATE INDEX idx_inst_req_student_status ON edu_institution_enrollment_request(student_id,status);
INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005801,'STUDENT_ENROLLMENT_INVITE_CREATE','创建班级入学邀请码','BUTTON',id,'STUDENT_ENROLLMENT_INVITE_CREATE',1,3910,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations';
INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005802,'STUDENT_ENROLLMENT_REQUEST_CREATE','申请关联教育机构','BUTTON',id,'STUDENT_ENROLLMENT_REQUEST_CREATE',1,3920,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations';
INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version)
SELECT 1874300000000005803,'STUDENT_ENROLLMENT_REVIEW','审核学生入学申请','BUTTON',id,'STUDENT_ENROLLMENT_REVIEW',1,3930,'ENABLED',1 FROM sys_menu WHERE type='PAGE' AND route='/organizations';
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY m.id),m.code,m.name,'BUTTON','WEB',pp.id,m.sort_order,'ENABLED','按实际家庭或机构班级范围处理入学申请'
FROM sys_menu m JOIN sys_menu parent ON parent.id=m.parent_id LEFT JOIN sys_permission pp ON pp.permission_code=parent.code
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_permission) base
WHERE m.code IN('STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REQUEST_CREATE','STUDENT_ENROLLMENT_REVIEW');
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY rp.role_id,p.id),rp.role_id,p.id,rp.effect
FROM sys_role_permission rp JOIN sys_permission old ON old.id=rp.permission_id AND old.permission_code='STUDENT_CLASS_ASSIGN'
JOIN sys_permission p ON p.permission_code IN('STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REVIEW')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=rp.role_id AND existing.permission_id=p.id);
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY rp.role_id,p.id),rp.role_id,p.id,rp.effect
FROM sys_role_permission rp JOIN sys_permission old ON old.id=rp.permission_id AND old.permission_code='STUDENT_CREATE'
JOIN sys_permission p ON p.permission_code='STUDENT_ENROLLMENT_REQUEST_CREATE'
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=rp.role_id AND existing.permission_id=p.id);
INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY up.user_id,p.id),up.user_id,p.id,up.effect
FROM sys_user_permission up JOIN sys_permission old ON old.id=up.permission_id AND old.permission_code='STUDENT_CLASS_ASSIGN'
JOIN sys_permission p ON p.permission_code IN('STUDENT_ENROLLMENT_INVITE_CREATE','STUDENT_ENROLLMENT_REVIEW')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_user_permission) base
WHERE NOT EXISTS(SELECT 1 FROM sys_user_permission existing WHERE existing.user_id=up.user_id AND existing.permission_id=p.id);
INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY up.user_id,p.id),up.user_id,p.id,up.effect
FROM sys_user_permission up JOIN sys_permission old ON old.id=up.permission_id AND old.permission_code='STUDENT_CREATE'
JOIN sys_permission p ON p.permission_code='STUDENT_ENROLLMENT_REQUEST_CREATE'
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_user_permission) base
WHERE NOT EXISTS(SELECT 1 FROM sys_user_permission existing WHERE existing.user_id=up.user_id AND existing.permission_id=p.id);
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY r.id),r.id,p.id,'ALLOW'
FROM sys_role r JOIN sys_permission p ON p.permission_code='STUDENT_ENROLLMENT_REQUEST_CREATE'
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE r.role_code='PARENT' AND NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=r.id AND existing.permission_id=p.id);
ALTER TABLE edu_student_organization_change DROP CONSTRAINT ck_student_organization_change_type;
ALTER TABLE edu_student_organization_change ADD CONSTRAINT ck_student_organization_change_type CHECK(change_type IN('CLASS_ASSIGN','CLASS_TRANSFER','CLASS_DEACTIVATE','ENROLLMENT_DEACTIVATE'));
