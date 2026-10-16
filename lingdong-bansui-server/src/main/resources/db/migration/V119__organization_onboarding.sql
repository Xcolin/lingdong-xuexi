CREATE TABLE sys_organization_owner (
 organization_id BIGINT NOT NULL PRIMARY KEY,
 user_id BIGINT NOT NULL,
 version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_onboarding_owner_org FOREIGN KEY (organization_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_onboarding_owner_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE sys_organization_onboarding_application (
 id BIGINT NOT NULL PRIMARY KEY,
 applicant_id BIGINT NOT NULL,
 type_code VARCHAR(32) NOT NULL,
 name VARCHAR(128) NOT NULL,
 normalized_name VARCHAR(128) NOT NULL,
 region_id BIGINT NOT NULL,
 contact_name VARCHAR(64) NOT NULL,
 mobile VARCHAR(32) NOT NULL,
 status VARCHAR(16) NOT NULL,
 task_id BIGINT,
 organization_id BIGINT,
 rejection_reason VARCHAR(1000),
 submission_batch INT NOT NULL DEFAULT 1,
 version_no INT NOT NULL DEFAULT 1,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_onboarding_applicant FOREIGN KEY(applicant_id) REFERENCES sys_user(id),
 CONSTRAINT fk_onboarding_region FOREIGN KEY(region_id) REFERENCES sys_organization(id),
 CONSTRAINT fk_onboarding_created_org FOREIGN KEY(organization_id) REFERENCES sys_organization(id),
 CONSTRAINT uk_onboarding_task UNIQUE(task_id)
);
CREATE INDEX idx_onboarding_applicant ON sys_organization_onboarding_application(applicant_id,created_at);
CREATE TABLE sys_organization_onboarding_event (
 id BIGINT NOT NULL PRIMARY KEY,
 application_id BIGINT NOT NULL,
 submission_batch INT NOT NULL,
 event_type VARCHAR(16) NOT NULL,
 operator_id BIGINT NOT NULL,
 reason VARCHAR(1000),
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_onboarding_event_application FOREIGN KEY(application_id) REFERENCES sys_organization_onboarding_application(id),
 CONSTRAINT fk_onboarding_event_operator FOREIGN KEY(operator_id) REFERENCES sys_user(id)
);
CREATE TABLE sys_organization_onboarding_family_request (
 user_id BIGINT NOT NULL,
 idempotency_key VARCHAR(64) NOT NULL,
 organization_id BIGINT NOT NULL,
 name VARCHAR(128) NOT NULL,
 PRIMARY KEY(user_id,idempotency_key),
 CONSTRAINT fk_onboarding_family_user FOREIGN KEY(user_id) REFERENCES sys_user(id),
 CONSTRAINT fk_onboarding_family_org FOREIGN KEY(organization_id) REFERENCES sys_organization(id)
);
-- Reservation is held while pending and after approval. Released on withdrawal/rejection.
-- Its unique key serializes competing applicants independently of generated organization codes.
CREATE TABLE sys_organization_onboarding_institution_key (
 type_code VARCHAR(32) NOT NULL,
 normalized_name VARCHAR(128) NOT NULL,
 region_id BIGINT NOT NULL,
 application_id BIGINT NOT NULL,
 PRIMARY KEY(type_code,normalized_name,region_id),
 CONSTRAINT fk_onboarding_key_application FOREIGN KEY(application_id) REFERENCES sys_organization_onboarding_application(id)
);
CREATE TABLE auth_verified_mobile (
 user_id BIGINT NOT NULL PRIMARY KEY,
 mobile VARCHAR(32) NOT NULL,
 verified_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_verified_mobile_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
