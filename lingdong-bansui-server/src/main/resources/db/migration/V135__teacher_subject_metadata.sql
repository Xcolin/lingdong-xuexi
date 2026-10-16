-- Optional subject metadata scoped to each teaching institution; no historical rows are changed.
CREATE TABLE edu_teacher_subject_metadata (
 teacher_user_id BIGINT NOT NULL,
 institution_organization_id BIGINT NOT NULL,
 subject_code VARCHAR(64) NULL,
 version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (teacher_user_id,institution_organization_id),
 CONSTRAINT fk_teacher_subject_user FOREIGN KEY (teacher_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_teacher_subject_org FOREIGN KEY (institution_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT ck_teacher_subject_nonblank CHECK (subject_code IS NULL OR CHAR_LENGTH(TRIM(subject_code)) > 0),
 CONSTRAINT ck_teacher_subject_version CHECK (version_no > 0)
);

-- Append-only history; each metadata version has exactly one recorded change.
CREATE TABLE edu_teacher_subject_audit (
 teacher_user_id BIGINT NOT NULL,
 institution_organization_id BIGINT NOT NULL,
 version_no INT NOT NULL,
 operator_user_id BIGINT NOT NULL,
 before_subject_code VARCHAR(64) NULL,
 after_subject_code VARCHAR(64) NULL,
 occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (teacher_user_id,institution_organization_id,version_no),
 CONSTRAINT fk_teacher_subject_audit_user FOREIGN KEY (teacher_user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_teacher_subject_audit_org FOREIGN KEY (institution_organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_teacher_subject_audit_actor FOREIGN KEY (operator_user_id) REFERENCES sys_user(id),
 CONSTRAINT ck_teacher_subject_audit_version CHECK (version_no > 0)
);
