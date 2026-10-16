# 校区、校内成员与默认角色 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task.

**Goal:** 完成用户批准的五项校区及关联操作需求，保持操作直接清晰。
**Architecture:** 复用组织树和学校祖先；用户选择器共用双栏模型，后端学校与实际范围双校验；默认角色事务分配。
**Tech Stack:** Spring Boot/MyBatis/MySQL、React/Ant Design。

- [x] 校区/年级接口、组织创建移动、教育业务学校边界和层级排序；回归校内跨校区及跨校拒绝。
- [x] 同校用户候选/已关联查询与批量增减、管理员角色范围一致性、账号默认角色及V104；回归范围/多组织回收/默认角色。
- [x] 共用左右用户选择器、组织过滤排序、RoleUsers关联增减、校区UI、教师创建选项；前端回归构建。
- [ ] 独立复核、串行Maven验证、真实数据库迁移及服务重启、真实接口与浏览器验证。

分区文件归属避免并发覆盖；Maven由root串行授予。不得提交/回退历史脏改，不清空业务数据，不更改现有凭据。新增默认基础角色无管理业务授权，迁移只增所需角色，不自动批量授予已有平台/机构账号。

## 2026-10-08 收尾验证

后端最终专项46项通过；此前IAM/目录专项36项通过（包含重叠8项，不按相加作为唯一数量）。教师源范围HTTP回归先复现404预期/200实际，修复后绑定与解除均拒绝校区范围外教师，ALL学校操作者可同校跨校区绑定，返回组织全路径。校区错误提示与旧年级缺学校祖先空值保护补齐。教师选项保留classOrganizationIds并新增organizationNames，按组织DFS排序。

前端初次全量397通过/4失败，四项均通过修复后专项验证，未重复声明全套通过；相关组织层级/创建26项、教师路径18项及角色/双栏专项、目录一致性通过，tsc+vite build exit0。

后端package exit0；运行会话8080、5173、5174保持，真实健康UP且db/redis=true，Flyway校验105项且MySQL版本104。真实接口只读烟测通过：管理员登录、校区/学校/年级/班级、成员及管理员左右列表、角色左右关联、默认身份角色、教师路径、原四年级三班向思翰关系。没有删除业务数据或改现有密码。

最后一项勾选暂保留：独立复核发现的教师P1/P2已由root修正并用接口回归验证，但浏览器实际点击验收未完成；CUA初始化报helper_unknown_error，正常编辑通道另受reparse限制。一次大命令自动审批因usage limit中断；拆分为明确项目文件的编辑与隔离测试并重新请求审批后获准完成。

证据：.local-verification/campus-final-backend-tests.log、campus-teacher-source-red.log、campus-web-final-build.log、campus-menu-catalog-final.log、campus-final-package.log、campus-final-server.log、campus-final-runtime-verification.log。
