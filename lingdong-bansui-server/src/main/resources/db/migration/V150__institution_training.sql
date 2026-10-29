CREATE TABLE org_training_program_registration (
 program_organization_id BIGINT NOT NULL PRIMARY KEY,
 institution_organization_id BIGINT NOT NULL,
 program_type VARCHAR(16) NOT NULL,
 enrollable VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 updated_by BIGINT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(program_organization_id) REFERENCES sys_organization(id),
 FOREIGN KEY(institution_organization_id) REFERENCES sys_organization(id),
 FOREIGN KEY(updated_by) REFERENCES sys_user(id),
 CHECK(program_type IN('OFFICIAL_COURSE','CLUB_ACTIVITY')),
 CHECK(enrollable IN('ENABLED','DISABLED'))
);
CREATE INDEX idx_training_program_institution ON org_training_program_registration(institution_organization_id,enrollable,program_type);
CREATE TABLE edu_institution_training_request (
 id BIGINT NOT NULL PRIMARY KEY,
 student_id BIGINT NOT NULL,
 family_organization_id BIGINT NOT NULL,
 institution_organization_id BIGINT NOT NULL,
 program_organization_id BIGINT NOT NULL,
 request_type VARCHAR(16) NOT NULL,
 reason VARCHAR(200) NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 pending_guard BIGINT NULL,
 requested_by BIGINT NOT NULL,
 reviewed_by BIGINT NULL,
 reviewed_at TIMESTAMP NULL,
 review_reason VARCHAR(200) NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(student_id) REFERENCES edu_student(id),
 FOREIGN KEY(family_organization_id) REFERENCES sys_organization(id),
 FOREIGN KEY(institution_organization_id) REFERENCES sys_organization(id),
 FOREIGN KEY(program_organization_id) REFERENCES org_training_program_registration(program_organization_id),
 FOREIGN KEY(requested_by) REFERENCES sys_user(id),
 FOREIGN KEY(reviewed_by) REFERENCES sys_user(id),
 UNIQUE(student_id,program_organization_id,pending_guard),
 CHECK(request_type IN('OFFICIAL_COURSE','CLUB_ACTIVITY')),
 CHECK(status IN('PENDING','APPROVED','REJECTED','WITHDRAWN')),
 CHECK((status='PENDING' AND pending_guard IS NOT NULL AND pending_guard=1) OR (status<>'PENDING' AND pending_guard IS NULL))
);
CREATE INDEX idx_training_request_family ON edu_institution_training_request(family_organization_id,status);
CREATE INDEX idx_training_request_institution ON edu_institution_training_request(institution_organization_id,status);
CREATE INDEX idx_training_request_student ON edu_institution_training_request(student_id,status);
CREATE TABLE edu_training_participation (
 id BIGINT NOT NULL PRIMARY KEY,
 training_request_id BIGINT NOT NULL UNIQUE,
 student_id BIGINT NOT NULL,
 program_organization_id BIGINT NOT NULL,
 institution_organization_id BIGINT NOT NULL,
 status VARCHAR(16) NOT NULL,
 active_guard BIGINT NULL,
 joined_at TIMESTAMP NOT NULL,
 left_at TIMESTAMP NULL,
 FOREIGN KEY(training_request_id) REFERENCES edu_institution_training_request(id),
 FOREIGN KEY(student_id) REFERENCES edu_student(id),
 FOREIGN KEY(program_organization_id) REFERENCES org_training_program_registration(program_organization_id),
 FOREIGN KEY(institution_organization_id) REFERENCES sys_organization(id),
 UNIQUE(student_id,program_organization_id,active_guard),
 CHECK(status IN('ACTIVE','LEFT')),
 CHECK((status='ACTIVE' AND active_guard IS NOT NULL AND active_guard=1) OR (status='LEFT' AND active_guard IS NULL))
);
CREATE INDEX idx_training_participation_student ON edu_training_participation(student_id,status);
CREATE INDEX idx_training_participation_program ON edu_training_participation(program_organization_id,status);

-- Both clients use the same permission catalog and grantable menu actions.
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,status)
SELECT 2201000000000150001,'STUDENT_TRAINING_REQUEST_CREATE','培训申请 · 提交与撤回','BUTTON','BOTH',id,'ENABLED' FROM sys_permission WHERE permission_code='PARENT_RELATIONSHIP_READ' AND resource_type='PAGE';
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,status)
SELECT 2201000000000150002,'STUDENT_TRAINING_REQUEST_REVIEW','培训申请 · 审核','BUTTON','BOTH',id,'ENABLED' FROM sys_permission WHERE permission_code='ORG_NODE_READ' AND resource_type='PAGE';
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,status)
SELECT 2201000000000150003,'STUDENT_TRAINING_PROGRAM_MANAGE','培训项目 · 登记与启停','BUTTON','BOTH',id,'ENABLED' FROM sys_permission WHERE permission_code='ORG_NODE_READ' AND resource_type='PAGE';
INSERT INTO sys_menu(id,code,name,type,parent_id,permission_code,grantable,sort_order,status,version)
SELECT p.id,p.permission_code,p.permission_name,'BUTTON',m.id,p.permission_code,1,950,'ENABLED',0 FROM sys_permission p JOIN sys_permission parent ON parent.id=p.parent_id AND parent.resource_type='PAGE' JOIN sys_menu m ON m.code=parent.permission_code AND m.type='PAGE' WHERE p.permission_code IN('STUDENT_TRAINING_REQUEST_CREATE','STUDENT_TRAINING_REQUEST_REVIEW','STUDENT_TRAINING_PROGRAM_MANAGE');

-- Controlled mirrors of established enrollment actions. ALLOW requires an explicitly allowed parent page; DENY is preserved without widening access.
INSERT INTO sys_role_permission(id,role_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY rp.role_id,p.id),rp.role_id,p.id,rp.effect
FROM sys_role_permission rp JOIN sys_permission old ON old.id=rp.permission_id
JOIN sys_permission p ON (old.permission_code='STUDENT_ENROLLMENT_REQUEST_CREATE' AND p.permission_code='STUDENT_TRAINING_REQUEST_CREATE') OR (old.permission_code='STUDENT_ENROLLMENT_REVIEW' AND p.permission_code='STUDENT_TRAINING_REQUEST_REVIEW') OR (old.permission_code='STUDENT_ENROLLMENT_INVITE_CREATE' AND p.permission_code='STUDENT_TRAINING_PROGRAM_MANAGE')
JOIN sys_role r ON r.id=rp.role_id AND r.status='ENABLED'
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_role_permission) base
WHERE (rp.effect='DENY' OR (rp.effect='ALLOW' AND EXISTS(SELECT 1 FROM sys_role_permission page WHERE page.role_id=rp.role_id AND page.permission_id=p.parent_id AND page.effect='ALLOW')))
AND NOT EXISTS(SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=rp.role_id AND existing.permission_id=p.id);
INSERT INTO sys_user_permission(id,user_id,permission_id,effect)
SELECT base.max_id+ROW_NUMBER() OVER(ORDER BY up.user_id,p.id),up.user_id,p.id,up.effect
FROM sys_user_permission up JOIN sys_permission old ON old.id=up.permission_id
JOIN sys_permission p ON (old.permission_code='STUDENT_ENROLLMENT_REQUEST_CREATE' AND p.permission_code='STUDENT_TRAINING_REQUEST_CREATE') OR (old.permission_code='STUDENT_ENROLLMENT_REVIEW' AND p.permission_code='STUDENT_TRAINING_REQUEST_REVIEW') OR (old.permission_code='STUDENT_ENROLLMENT_INVITE_CREATE' AND p.permission_code='STUDENT_TRAINING_PROGRAM_MANAGE')
CROSS JOIN(SELECT COALESCE(MAX(id),0) max_id FROM sys_user_permission) base
WHERE (up.effect='DENY' OR (up.effect='ALLOW' AND EXISTS(SELECT 1 FROM sys_user_permission page WHERE page.user_id=up.user_id AND page.permission_id=p.parent_id AND page.effect='ALLOW')))
AND NOT EXISTS(SELECT 1 FROM sys_user_permission existing WHERE existing.user_id=up.user_id AND existing.permission_id=p.id);

