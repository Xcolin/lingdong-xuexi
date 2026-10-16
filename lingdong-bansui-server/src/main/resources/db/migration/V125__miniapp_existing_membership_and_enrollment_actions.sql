-- Reuse existing grants and data scopes; change only the supported permission client.
UPDATE sys_permission SET client_type = 'BOTH'
WHERE client_type = 'WEB' AND permission_code IN (
  'STUDENT_CREATE', 'STUDENT_READ',
  'ORG_MEMBER_READ', 'ORG_MEMBER_ASSOCIATE', 'ORG_MEMBER_REMOVE',
  'ORG_ADMIN_ASSIGN', 'ORG_ADMIN_REVOKE',
  'STUDENT_ENROLLMENT_INVITE_CREATE', 'STUDENT_ENROLLMENT_REQUEST_CREATE',
  'STUDENT_ENROLLMENT_REVIEW'
);
