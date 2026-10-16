CREATE TABLE auth_initial_password_state (
 user_id BIGINT NOT NULL PRIMARY KEY,
 change_required BOOLEAN NOT NULL DEFAULT TRUE,
 updated_at DATETIME NOT NULL,
 CONSTRAINT fk_initial_password_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
UPDATE sys_permission SET client_type='BOTH' WHERE permission_code='IAM_USER_PASSWORD_SET';
