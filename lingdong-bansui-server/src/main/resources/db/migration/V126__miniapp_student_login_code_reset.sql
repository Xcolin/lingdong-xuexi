-- Reuse existing grants and guardian scope; expose the existing reset operation to both clients.
UPDATE sys_permission SET client_type='BOTH' WHERE permission_code='STUDENT_LOGIN_CODE_RESET';
