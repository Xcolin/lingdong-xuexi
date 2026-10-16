# 组织首次入驻实施计划

> For agentic workers: 使用 superpowers:executing-plans 按任务执行，完成每阶段验证后再进入下一阶段。

**Goal:** 提供家庭自助创建、学校/培训机构申请审核及移动端我的组织入口，自动完成组织关联和管理员授权。

**Architecture:** 一个账号保留原角色，管理员角色绑定具体组织；后端统一处理 Web/MINIAPP 入驻事务。机构申请复用系统任务审核框架，家庭直接创建；复用现有邀请与入学流程。本计划不授权删除或重新初始化现有数据。

**Tech Stack:** Spring Boot、MyBatis、MySQL/Flyway；React/Ant Design；uni-app/Vue。

## 一、已确认范围

- 家庭填写名称直接创建，申请人成为负责人及家庭管理员 PARENT_ADMIN。
- 学校、培训机构填写类型、名称、区域、联系人、已验证手机号，平台审核通过后创建组织并授予该组织 ORG_ADMIN。
- 用户可查看自己的申请；待审核可撤回；驳回/撤回后可修改并重新提交。
- 平台维护国家、城市、区域；机构内部创建校区、年级、班级，入驻入口不创建下级。
- 复用邀请码加入、邀请家人、学生入学；家庭创建不要求选择学校。
- 最后一名管理员退出/取消管理员前须转交；负责人必须保持有效管理员身份。
- 入驻不等于认证，不新增认证材料、机构认领、组织解散、复杂风控、全平台机构搜索。
- 同一用户可具有多个组织角色，禁止覆盖已有角色或将其他组织权限带入当前组织。

## 二、现状与必要约束

已核对：
- `lingdong-bansui-server/src/main/java/com/lingdong/learning/iam/application/OrganizationMembershipService.java` 已维护固定管理员角色，但家庭管理员仍要求 FAMILY 用户类型。
- `lingdong-bansui-server/src/main/java/com/lingdong/learning/audit/application/SystemTaskType.java` 有通用审核任务，尚无机构入驻类型。
- `lingdong-bansui-miniapp/src/session/organization-session.ts` 单独保存机构会话，不能仅改变 UI 标签就实现组织身份切换。
- `lingdong-bansui-miniapp/src/pages.json` 已有家长、机构、教师首页及家长初始化页。
- 权限规范模型为 `lingdong-bansui-server/src/main/resources/authorization-model.json`，菜单由生成工具发布。

兼容改造只处理入驻及其后管理员入口：依据有效身份档案、组织角色与范围判断，不将 user_type 改成另一类型，不放开学生入驻，不用前端组织 ID 替代后端校验。教师专业身份仍需现有教师建档流程，不由机构入驻自动生成。若现有单类型限制影响这条闭环，应补最小身份兼容测试及实现，不连带重写其他模块。

## 三、数据与接口设计

新增表，迁移版本以实施时最新 Flyway 版本为准，不能改写已有成功迁移：
- `sys_organization_onboarding_application`：申请人、机构类型/名称/区域、联系人/手机号快照、状态、审核任务ID、创建组织ID、驳回原因、提交批次、版本、时间。
- `sys_organization_onboarding_event`：提交、撤回、通过、驳回事件及操作者，保留重新提交历史。
- `sys_organization_owner`：组织负责人，一组织一负责人；仅用于负责人转交和最后管理员保护，不替代组织管理员关系。

状态：DRAFT（表单本地，不入库）→ PENDING → APPROVED/REJECTED/WITHDRAWN；REJECTED/WITHDRAWN 可更新后重新进入 PENDING，旧审核结果不可覆盖当前批次。

组织编码由后端生成，申请人不填写；家庭允许重名。学校/培训机构按类型、规范化名称、区域识别重复待审核申请及已存在机构：明确重复提示联系平台，疑似重复展示给审核员；本期不提供认领流程。审核时再次查重，组织唯一约束为并发最终防线。

建议新增 `/api/v1/organization-onboarding`：
- POST `/families`：家庭名称＋幂等键，返回组织及本人管理员关系。
- GET `/applications`、GET `/applications/{id}`：仅本人。
- POST `/applications`：学校/培训机构申请。
- POST `/applications/{id}/withdraw`、POST `/applications/{id}/resubmit`：版本号及当前批次校验。
- GET `/my-organizations`：本人有效组织及角色、负责人标记、可进入能力。
- POST `/organizations/{id}/owner-transfer`：已有管理员作为目标，事务内完成转交。

平台审核入口复用 `/system-tasks`，新增 ORGANIZATION_ONBOARDING 任务类型。建立明确提交/查看/审核权限与页面归属；登录用户个人申请能力不要求已有机构管理员角色，Web 和 MINIAPP 客户端分别声明。通用任务审核必须分派到入驻服务校验权限、状态、批次，不能只改任务状态。

## 四、实施任务

### 任务1：身份兼容与方案落地检查
- [ ] 写账号保留原身份、增加家庭/机构角色的失败测试。
- [ ] 核对登录、角色兼容校验、家长初始化拦截器及管理员入口中与入驻有关的单类型条件。
- [ ] 明确最小档案/能力映射，记录到设计说明；拒绝学生申请与跨组织切换。
- [ ] 验证现有家长、教师、学生登录与组织用户功能不回归。

涉及：`iam/application/OrganizationMembershipService.java`、`user/application/RoleIdentityCompatibilityService.java`、`common/security/ParentOnboardingAccessInterceptor.java`、认证当前用户能力服务。仅按检查结果修改必要分支。

### 任务2：数据迁移与入驻服务
新增 `organization/onboarding/{application,domain,infrastructure/persistence,web}`；新增 mapper XML、Flyway 增量迁移和专项测试。
- [ ] 先写状态机、重复提交、非本人访问、乐观锁及并发审核失败测试。
- [ ] 实现申请保存、撤回、修改重提，验证区域必须为现有有效组织。
- [ ] 实现家庭事务：创建组织、本人关联、固定 PARENT_ADMIN＋FAMILY 范围、负责人记录；失败全部回滚。
- [ ] 实现机构通过事务：锁申请、再次查重、创建组织、关联用户、固定 ORG_ADMIN＋机构范围、负责人、完成审核。
- [ ] 幂等重试返回同一个组织，禁止产生重复授权和组织。

### 任务3：审核与统一权限
修改系统任务类型/分派服务及 `lingdong-bansui-web/src/features/system-tasks/SystemTaskWorkbench.tsx`，更新规范授权模型及生成目录。
- [ ] 写已授权审核成功、无审核权限拒绝、重复审核/撤回后审核拒绝测试。
- [ ] 复用任务详情展示机构信息、重复提示，操作明确命名“通过入驻”“驳回入驻”。
- [ ] 驳回原因必填，不增加认证/认领操作。
- [ ] 新编码在授权树、菜单、当前有效权限、API 执行保持一致；个人申请与平台审核严格分离。

### 任务4：负责人转交与退出保护
复用现有组织管理员关系，不新增通用角色配置页。
- [ ] 写最后管理员移除/退出拒绝、转交成功、跨组织目标拒绝测试。
- [ ] 将保护放入所有实际移除入口，包括组织取消管理员和角色关联移除，不能只隐藏按钮。
- [ ] 负责人转交仅选当前组织有效管理员；转交本身不扩大其原有权限，不自动撤销原负责人角色。
- [ ] 停用最后管理员账号的已有路径执行一致保护，平台异常处理不在本期新增。

### 任务5：Web 入驻入口
新增 `lingdong-bansui-web/src/features/organization-onboarding/` 与 `src/api/organizationOnboarding.ts`，按现有路由/规范菜单注册入口。
- [ ] 写家庭创建、机构申请、本人申请查询及失败保留表单测试。
- [ ] 登录后提供“我的组织/入驻”入口；不强制跳转，不要求已有组织才能进入。
- [ ] 创建成功刷新本人组织与权限；审批结果显示进入组织入口。
- [ ] 查询/重置/取消不配置独立权限，继续统一布局与分页。

### 任务6：移动端最小页面
新增 `src/api/organization-onboarding.ts`、`src/pages/my-organizations/my-organizations.vue`、`src/pages/organization-onboarding/organization-onboarding.vue`、`src/pages/organization-application/organization-application.vue`；更新 `src/pages.json` 和已有个人入口。
- [ ] 从“我的→我的组织”进入；无组织显示“创建家庭”“学校/培训机构入驻”，邀请码加入复用已有入口。
- [ ] 一个简洁表单支持两类流程，家庭仅名称；机构四类信息，手机号使用已验证账号信息。
- [ ] 展示待审核/通过/驳回/撤回及相应操作，网络失败保留输入，防重复提交。
- [ ] 按实际角色进入既有家庭或机构页面；组织切换刷新数据，清除上一组织选中对象和缓存。
- [ ] 不混用家长/机构 token，复用认证策略提供有效上下文；直接访问页面也必须校验。
- [ ] 现有首次家长初始化不重复创建家庭，不因未有家庭而挡住入驻入口。

### 任务7：验收与发布
- [ ] 后端专项测试：家庭即时生效、机构先申请后创建、幂等、并发、角色保留、范围隔离、管理员保护。
- [ ] Web 相关 Vitest、`npm run build`；移动端 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`。
- [ ] 隔离 MySQL 执行增量迁移与真实 API 验证，不以 H2 成功代替 MySQL 成功。
- [ ] 实际 Web/移动端走完创建家庭、申请机构、驳回重提、通过、切换组织；核对授权树/me/菜单/API。
- [ ] 保留已有组织用户与角色、全局开关和运行密钥；备份后部署增量迁移，记录健康检查及验证报告。

后端运行命令按任务指定测试类：`mvn -Dtest=OrganizationOnboardingApplicationServiceTest,OrganizationOnboardingApiIntegrationTest,OrganizationOwnerTransferTest test`；新增类名按此计划保持一致。Microsoft JDK17。所有数据库写入都需事务与范围校验；不关闭外键、不重建数据库。

## 五、验收底线

1. 创建家庭后能实际进入和管理该家庭，不只是拥有角色记录。
2. 待审核机构不产生可管理组织，通过一次只产生一个组织。
3. 角色新增不覆盖原角色，也不能获得其他组织管理权。
4. 两个家庭/学校之间的数据及操作范围隔离。
5. 最后管理员保护覆盖前后端与所有移除路径。
6. Web/移动端共享业务规则，菜单与 API 一致，旧流程不被入驻强制打断。
7. 现有数据保留，未做认证、认领、解散或无关模块扩展。

## 六、实施顺序与检查点

身份兼容与测试 → 增量数据/入驻事务 → 审核/权限 → 管理员保护 → Web → 移动端 → 隔离验证 → 增量部署。每阶段先失败测试、最小实现、验证通过，再进入下一阶段。计划已形成，当前未实施功能、未执行迁移。

## 2026-10-11 最终实施状态

本计划实施并增量部署至 V120；保留现有数据。验证明细与剩余验收边界见 docs/organization-onboarding-verification-2026-10-11.md。家庭/机构首期流程及移动端组织范围已经实现；微信真机和实际短信送达留作真实通道验收。
