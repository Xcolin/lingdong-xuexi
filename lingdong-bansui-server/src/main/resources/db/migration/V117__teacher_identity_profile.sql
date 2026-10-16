-- Teacher identity is an account profile, independent of current role grants.
CREATE TABLE edu_teacher_profile (
  user_id BIGINT NOT NULL PRIMARY KEY,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_teacher_profile_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
);
INSERT INTO edu_teacher_profile(user_id,status)
SELECT DISTINCT u.id,'ENABLED' FROM sys_user u
WHERE u.user_type='ORGANIZATION' AND (
 EXISTS(SELECT 1 FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='TEACHER')
 OR EXISTS(SELECT 1 FROM edu_teacher_class tc WHERE tc.teacher_user_id=u.id)
);
