-- V138：恢复学生角色的业务附件读取权限。
-- V31 曾将 ATTACHMENT_READ 授予内置 STUDENT 角色（学生读取本人打卡图片与任务示例图），
-- V118 规范化授权模型时该授予被清理，导致小程序端打卡附件与示例图内容下载 403。按角色码补授。
INSERT INTO sys_role_permission (id, role_id, permission_id)
SELECT 2111461000000000001, r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'ATTACHMENT_READ' AND p.status = 'ENABLED'
WHERE r.role_code = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = r.id AND existing.permission_id = p.id
  );
