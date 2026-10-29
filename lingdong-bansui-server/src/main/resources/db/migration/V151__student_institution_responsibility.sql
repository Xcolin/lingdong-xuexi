-- Institution-scoped responsibility. Never changes edu_student or organization owners.
CREATE TABLE edu_student_institution_responsibility (
    student_id BIGINT NOT NULL,
    institution_organization_id BIGINT NOT NULL,
    responsible_user_id BIGINT NULL,
    version_no INT NOT NULL DEFAULT 1,
    updated_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id,institution_organization_id),
    CONSTRAINT fk_responsibility_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_responsibility_institution FOREIGN KEY (institution_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_responsibility_user FOREIGN KEY (responsible_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_responsibility_version CHECK (version_no > 0)
);
CREATE TABLE edu_student_responsibility_transfer (
    student_id BIGINT NOT NULL,
    institution_organization_id BIGINT NOT NULL,
    from_user_id BIGINT NULL,
    to_user_id BIGINT NOT NULL,
    reason_type VARCHAR(32) NOT NULL,
    reason_text VARCHAR(200) NULL,
    transferred_by BIGINT NOT NULL,
    before_version_no INT NOT NULL,
    after_version_no INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id,institution_organization_id,after_version_no),
    CONSTRAINT fk_responsibility_transfer_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_responsibility_transfer_institution FOREIGN KEY (institution_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT ck_responsibility_reason CHECK (reason_type IN ('WORK_ADJUSTMENT','RESIGNATION_HANDOVER','TEMPORARY_TAKEOVER','OTHER')),
    CONSTRAINT ck_responsibility_other CHECK (reason_type <> 'OTHER' OR (reason_text IS NOT NULL AND CHAR_LENGTH(TRIM(reason_text)) > 0)),
    CONSTRAINT ck_responsibility_transfer_version CHECK (before_version_no >= 0 AND after_version_no = before_version_no + 1)
);
CREATE INDEX idx_responsibility_transfer_time ON edu_student_responsibility_transfer(student_id,institution_organization_id,created_at);
