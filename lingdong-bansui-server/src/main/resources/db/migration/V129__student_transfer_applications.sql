-- Independent transfer proposal. Submitting does not modify enrollment/class relations.
CREATE TABLE edu_transfer_application (
    id BIGINT NOT NULL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    source_institution_id BIGINT NOT NULL,
    family_organization_id BIGINT NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    applicant_client_type VARCHAR(16) NOT NULL DEFAULT 'MINIAPP',
    target_description VARCHAR(200) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    effective_date DATE NOT NULL,
    attachment_ids VARCHAR(2048) NOT NULL DEFAULT '[]',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reviewer_user_id BIGINT NULL,
    reviewer_client_type VARCHAR(16) NULL,
    review_reason VARCHAR(200) NULL,
    reviewed_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    execution_blocked TINYINT NOT NULL DEFAULT 0,
    execution_error_code VARCHAR(64) NULL,
    version_no INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    active_scope_key VARCHAR(100) GENERATED ALWAYS AS
        (CASE WHEN status IN ('PENDING','APPROVED') THEN CONCAT(CAST(student_id AS CHAR(20)), ':', CAST(source_institution_id AS CHAR(20))) ELSE NULL END),
    CONSTRAINT uk_transfer_active_scope UNIQUE (active_scope_key),
    CONSTRAINT fk_transfer_student FOREIGN KEY (student_id) REFERENCES edu_student(id),
    CONSTRAINT fk_transfer_source FOREIGN KEY (source_institution_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_transfer_family FOREIGN KEY (family_organization_id) REFERENCES sys_organization(id),
    CONSTRAINT fk_transfer_applicant FOREIGN KEY (applicant_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_transfer_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
    CONSTRAINT ck_transfer_status CHECK (status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN','COMPLETED')),
    CONSTRAINT ck_transfer_completed CHECK ((status='COMPLETED' AND completed_at IS NOT NULL) OR (status<>'COMPLETED' AND completed_at IS NULL)),
    CONSTRAINT ck_transfer_version CHECK (version_no>0)
);
CREATE INDEX idx_transfer_family_status ON edu_transfer_application(family_organization_id,status,created_at);
CREATE INDEX idx_transfer_source_status ON edu_transfer_application(source_institution_id,status,created_at);
CREATE INDEX idx_transfer_due ON edu_transfer_application(status,execution_blocked,effective_date);

