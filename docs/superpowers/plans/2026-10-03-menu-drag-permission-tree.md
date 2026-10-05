# 菜单拖动与树形授权实施计划

> 使用superpowers:subagent-driven-development：IAM纵向子任务负责后端批量授权与角色树前端，主代理负责菜单拖动/批量按钮；独立文件范围，集成后审查。保留历史脏改动，不提交或重置。

**Goal:** 菜单拖动调整顺序与层级，按钮批量新增、编码作为权限编码，角色树级联批量授权。
**Architecture:** 复用现有menu order/position/buttons:batch接口；Menu树拖动计算目标父级与索引，原子提交成功刷新导航。IAM权限树结合菜单与其他业务权限，显式ALLOW批量保存，保留DENY及用户独立授权；服务端事务校验与并发快照冲突。
**Tech Stack:** React/AntDesign/Vitest、SpringBoot/MyBatis/Flyway。

- [x] IAM：先红测试，新增PUT roles/{id}/permissions:batch body {permissionIds,managedPermissionIds,expectedAssignments}，明确范围；仅替换范围内ALLOW、保留DENY/范围外/用户授权；校验角色/所有权限/旧快照/权限访问，事务审计。前端RolePermissionTreeModal，目录/页面/按钮及其他权限，级联勾选与半选，DENY不可改，角色切换防旧响应覆盖；删除角色单条授权UI，保留用户独立授权UI。
- [x] 菜单：先红测试，接入Tree draggable，取消上下移按钮；同级调用order、跨父调用position，禁非法层级/自孙/受保护入口/查询状态下排序。新增批量按钮Drawer Form.List，编码即权限编码，默认grantable=true；新增页面也编码即权限编码，取消权限选择。成功刷新导航。
- [x] 集成审查：目录与后端新模型兼容、已有权限回显与DENY保存、防串角色、拖动版本冲突、批量错误原子性；相关测试/build，必要迁移只新增版本。
- [x] 真实服务：打包并重启后端、健康检查；浏览器验证树形页面、批量抽屉与导航同步、授权级联，保存截图；不擅自变更真实角色授权。

最终验收：前端10文件46测试通过、typecheck和生产构建通过；后端相关39测试通过。真实服务MySQL V96/Redis健康，Web5173与H5 5174可用。浏览器验证批量新增多行、角色级联勾选并取消未保存；拖动接口接线、非法层级、核心保护及并发冲突由自动测试验证。
