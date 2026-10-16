-- Keep family scope, configured permissions and user associations unchanged.
UPDATE sys_role SET role_type='BUILT_IN',built_in=1 WHERE role_code='PARENT_ADMIN';
