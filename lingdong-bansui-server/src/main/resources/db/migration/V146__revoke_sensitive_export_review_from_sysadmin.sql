-- EXPORT_SENSITIVE_REVIEW（敏感导出审核）是审计职能动作：仅限 SYS_AUDITOR，
-- 防止同一管理员提交并审批自己的敏感数据导出（V57 最小权限先例）。
-- V144 按目录不变量统一补授 SYS_ADMIN 时误含本动作，在此撤销。
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id AND r.role_code = 'SYS_ADMIN'
JOIN sys_permission p ON p.id = rp.permission_id AND p.permission_code = 'EXPORT_SENSITIVE_REVIEW';
