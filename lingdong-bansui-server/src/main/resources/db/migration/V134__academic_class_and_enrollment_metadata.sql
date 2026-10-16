-- Optional academic metadata. No historical identities or memberships are changed.
CREATE TABLE edu_class_academic_metadata (
    class_organization_id BIGINT NOT NULL,
    head_teacher_user_id BIGINT NULL,
    version_no INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (class_organization_id),
    CONSTRAINT fk_class_academic_org FOREIGN KEY (class_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_class_academic_teacher FOREIGN KEY (head_teacher_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_class_academic_version CHECK (version_no > 0)
);
CREATE TABLE edu_student_enrollment_metadata (
    student_id BIGINT NOT NULL,
    institution_organization_id BIGINT NOT NULL,
    student_number VARCHAR(64) NULL,
    version_no INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id,institution_organization_id),
    CONSTRAINT uk_enrollment_student_number UNIQUE (institution_organization_id,student_number),
    CONSTRAINT fk_enrollment_metadata_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_enrollment_metadata_org FOREIGN KEY (institution_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT ck_enrollment_number_not_blank CHECK (student_number IS NULL OR CHAR_LENGTH(TRIM(student_number)) > 0),
    CONSTRAINT ck_enrollment_metadata_version CHECK (version_no > 0)
);

-- Append-only academic configuration changes; retained independently of account deletion.
CREATE TABLE edu_academic_metadata_audit (
    metadata_kind VARCHAR(24) NOT NULL,
    target_id BIGINT NOT NULL,
    scope_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    operator_user_id BIGINT NOT NULL,
    before_value VARCHAR(64) NULL,
    after_value VARCHAR(64) NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (metadata_kind,target_id,scope_id,version_no),
    CONSTRAINT ck_academic_audit_kind CHECK (metadata_kind IN ('HEAD_TEACHER','STUDENT_NUMBER')),
    CONSTRAINT ck_academic_audit_version CHECK (version_no > 0)
);
