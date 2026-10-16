ALTER TABLE auth_verified_email ADD COLUMN maintenance_source VARCHAR(32) NOT NULL DEFAULT 'SELF_VERIFIED';
