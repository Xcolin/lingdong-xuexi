-- Fictional demo parents are initialized as ready for authorization end-to-end tests.
-- Real newly created parent accounts still pass the agreement and onboarding flow.
INSERT INTO auth_user_agreement_acceptance(id,user_id,agreement_type,agreement_version,client_type,source_address_hash,accepted_at)
SELECT 2190000000000800001,2190000000000010006,'PARENT_USER_AGREEMENT',config_value,'WEB',NULL,CURRENT_TIMESTAMP FROM sys_config WHERE config_key='auth.parent-agreement.current-version' AND status='ENABLED';
INSERT INTO auth_user_agreement_acceptance(id,user_id,agreement_type,agreement_version,client_type,source_address_hash,accepted_at)
SELECT 2190000000000800002,2190000000000010007,'PARENT_USER_AGREEMENT',config_value,'WEB',NULL,CURRENT_TIMESTAMP FROM sys_config WHERE config_key='auth.parent-agreement.current-version' AND status='ENABLED';
