-- Reuse the existing authorization and resource scope; never grant roles or enable stopped permissions.
UPDATE sys_permission SET client_type='BOTH' WHERE permission_code='STUDENT_CLASS_ASSIGN' AND client_type='WEB';
