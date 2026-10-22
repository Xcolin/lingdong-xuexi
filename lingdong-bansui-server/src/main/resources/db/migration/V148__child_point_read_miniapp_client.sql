-- 允许已获授权且存在有效监护关系的家长从小程序读取孩子积分。
-- 不增加角色/用户授权，不开放积分修正；现有显式拒绝与关系检查继续生效。
UPDATE sys_permission
SET client_type='BOTH'
WHERE permission_code='GROWTH_POINT_READ_CHILD' AND client_type='WEB';
