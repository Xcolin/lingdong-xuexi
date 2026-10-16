-- Existing accounts and grants remain unchanged. Contact proof has its own uniqueness boundary.
CREATE TABLE auth_verified_email (
 user_id BIGINT NOT NULL PRIMARY KEY,
 email VARCHAR(254) NOT NULL,
 verified_at DATETIME NOT NULL,
 CONSTRAINT uk_auth_verified_email UNIQUE (email),
 CONSTRAINT fk_auth_verified_email_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE auth_registration_agreement_acceptance (
 user_id BIGINT NOT NULL,
 version VARCHAR(64) NOT NULL,
 client_type VARCHAR(16) NOT NULL,
 accepted_at DATETIME NOT NULL,
 source_digest VARCHAR(64) NOT NULL,
 PRIMARY KEY(user_id,version),
 CONSTRAINT fk_registration_acceptance_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
ALTER TABLE sys_organization_onboarding_application MODIFY COLUMN mobile VARCHAR(32) NULL,
 ADD COLUMN email VARCHAR(254) NULL AFTER mobile;
