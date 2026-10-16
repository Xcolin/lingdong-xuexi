CREATE TABLE org_course_category_metadata (
 organization_id BIGINT NOT NULL PRIMARY KEY,
 stage_codes VARCHAR(2048) NOT NULL,
 subject_code VARCHAR(64) NOT NULL,
 description VARCHAR(100) NOT NULL DEFAULT '',
 CONSTRAINT fk_course_category_organization FOREIGN KEY (organization_id) REFERENCES sys_organization(id)
);

