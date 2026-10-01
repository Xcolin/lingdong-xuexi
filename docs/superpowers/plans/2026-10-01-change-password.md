# 修改密码实施计划

> 执行方式：superpowers:subagent-driven-development；后端子任务独立负责认证服务、接口及测试，主代理实现前端并集成审查。沿用当前脏工作区，不提交或重置历史改动。

**Goal:** 登录用户通过账号菜单验证旧密码并修改自己的密码，成功后所有设备退出。
**Architecture:** 认证接口只取当前身份；事务内校验账号、旧密码及PasswordPolicy，条件更新哈希并撤销全部会话。独立ChangePasswordModal组件负责校验、错误与加载状态，App提供入口及退出回调。
**Tech Stack:** Java17/SpringBoot/MyBatis、React/AntDesign/Vitest。

- [x] 后端先补集成失败测试，新增POST /api/v1/auth/password（oldPassword,newPassword），旧密码错误返回400保留会话；有效请求保存密码哈希并撤销全部会话；并发修改使用原哈希条件更新防止覆盖。验证弱密码、新旧相同、锁定账号、旧令牌与刷新令牌失效、新密码登录。必要时复用现有安全事件。
- [x] 前端先补失败测试，新增features/auth/ChangePasswordModal.tsx及测试，authApi.changePassword封装；右上角账号菜单入口。旧/新/确认密码输入，8—20位字母和数字组合、两次一致、新旧不同；失败保留输入，成功清理本地会话、关闭缓存并回到登录页；无左侧菜单或可配置业务动作依赖。
- [x] 审查规格与质量：只操作当前用户、凭证不记录、事务原子性、不会被账号安全开关阻止；密码输入autocomplete、重复提交及关闭期间请求处理。
- [x] 验收：相关Maven测试、Web相关测试与生产构建；打包重启真实后端并确认DB/Redis健康；浏览器验证账号入口/弹窗/校验，不改变真实admin密码。记录结果。

验收结果：后端46项通过（修改密码5、认证控制器6、认证应用服务7、机构登录10、家长密码认证18），含数据库锁并发先红后绿及明确400错误反馈。前端相关40项通过，最终弹窗/入口6项复核通过，类型检查及生产构建通过，diff --check通过。后端重新打包，持久密钥复用；无迁移变更。真实浏览器验证账号入口、弹窗、不一致密码阻止提交、旧密码错误保留会话，未更改真实admin密码。最终截图：.local-verification/change-password-final.png。
`n最终真实环境：health=UP、db=true、redis=true；浏览器明确收到旧密码错误，保持登录且允许重试，取消后再打开字段为空。
