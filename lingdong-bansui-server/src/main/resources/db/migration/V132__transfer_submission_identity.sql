-- Distinguish the actual operator from the guardian for institution-assisted proposals.
ALTER TABLE edu_transfer_application ADD COLUMN submission_mode VARCHAR(20) NOT NULL DEFAULT 'FAMILY';
ALTER TABLE edu_transfer_application ADD COLUMN primary_guardian_user_id BIGINT NULL;
UPDATE edu_transfer_application SET primary_guardian_user_id=applicant_user_id WHERE submission_mode='FAMILY';
ALTER TABLE edu_transfer_application ADD CONSTRAINT fk_transfer_primary_guardian FOREIGN KEY(primary_guardian_user_id) REFERENCES sys_user(id);
ALTER TABLE edu_transfer_application ADD CONSTRAINT ck_transfer_submission_mode CHECK(submission_mode IN ('FAMILY','INSTITUTION'));
