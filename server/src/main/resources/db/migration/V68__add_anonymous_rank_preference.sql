-- 家长主动查看偏好，不改变班级参榜名单。
CREATE TABLE growth_rank_preference (
    id BIGINT NOT NULL PRIMARY KEY,
    parent_user_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    class_organization_id BIGINT NOT NULL,
    enabled_flag BOOLEAN NOT NULL DEFAULT FALSE,
    version_no BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_growth_rank_preference UNIQUE(parent_user_id,student_id,class_organization_id),
    CONSTRAINT fk_rank_preference_parent FOREIGN KEY(parent_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_rank_preference_student FOREIGN KEY(student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_rank_preference_class FOREIGN KEY(class_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT ck_rank_preference_id CHECK(id >= 1000000000000000000),
    CONSTRAINT ck_rank_preference_version CHECK(version_no >= 1)
);
INSERT INTO sys_feature_toggle(id,feature_code,feature_name,scope_type,scope_key,status,built_in,description)
VALUES(1874244142494646801,'ANONYMOUS_CLASS_RANK','班级匿名积分排行','GLOBAL','GLOBAL','DISABLED',1,'默认关闭，家长主动开启本人查看偏好后仅查看孩子当前班级。');
INSERT INTO sys_permission(id,permission_code,permission_name,resource_type,client_type,parent_id,sort_order,status,description)
VALUES(1874244142494646802,'ANONYMOUS_CLASS_RANK_READ','查看孩子班级匿名排行','OPERATION','WEB',NULL,270,'ENABLED','只返回名次与班级来源积分，不允许跨班级访问。');
INSERT INTO sys_role_permission(id,role_id,permission_id)
SELECT 1874244142494646803,id,1874244142494646802 FROM sys_role WHERE role_code='PARENT';
