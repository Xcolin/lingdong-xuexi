-- V144 按目录不变量统一补授的 37 个 BUTTON 权限与各业务迁移确立的精确角色白名单冲突：
-- 导出/导出审核类动作（V57/V59/V61 等）断言可授权角色清单精确（最小权限，防越权批量导出），
-- SYS_ADMIN 不在其列。按钮授权以各业务迁移与 authorization-model.json policies 为准，
-- 在此撤销 V144 的统一补授（保留其权限名与菜单名同步部分）。
DELETE FROM sys_role_permission
WHERE id BETWEEN 1874300000000144001 AND 1874300000000144037
  AND role_id = (SELECT id FROM sys_role WHERE role_code = 'SYS_ADMIN');
