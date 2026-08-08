# 灵动学习 V25 积分账户与台账查询实施计划

> **执行要求：** 按测试驱动逐项实施；先运行失败用例，再写最小实现。当前目录存在连续里程碑改动，不自动提交或改写用户已有变更。

**目标：** 为学生本人和主家长提供受功能开关、RBAC、客户端类型与对象范围共同保护的积分账户及台账查询，并在 Web 与 uni-app 独立展示。

**架构：** Flyway V25 只新增开关与权限基础数据；`growthpoint` 模块新增只读查询边界。Web 只面向主家长，uni-app 只面向当前学生，不向教师或机构管理员暴露混合家庭来源的统一余额。

**技术栈：** Spring Boot 3、JDK 17、MyBatis XML、Flyway、MySQL 8/H2 测试、React + Ant Design Pro、uni-app + Vue 3。

---

## 任务一：V25 迁移与公开能力

**文件：**
- 新增：`server/src/main/resources/db/migration/V25__seed_growth_point_query_access.sql`
- 修改：`server/src/test/java/com/lingdong/learning/FlywayMigrationTest.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityController.java`
- 修改：`server/src/main/java/com/lingdong/learning/feature/web/PublicCapabilityResponse.java`
- 修改：`server/src/test/java/com/lingdong/learning/auth/web/StudentAuthenticationControllerTest.java`

- [ ] 在迁移测试中断言 V25、`GROWTH_POINT_QUERY`、两项权限、学生与家长授权。
- [ ] 运行 `mvn -Dtest=FlywayMigrationTest test`，确认因 V25 不存在而失败。
- [ ] 新增 V25 脚本，使用预生成 19 位雪花标识写入一个开关、两项权限和两条角色授权。
- [ ] 扩展公开能力响应并先更新接口用例，确认缺少字段时失败后再实现。
- [ ] 运行迁移与公开能力用例，确认 V1 至 V25 连续执行成功。

## 任务二：后端积分查询接口

**文件：**
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointQueryMapper.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointAccountViewRow.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointLedgerViewRow.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/infrastructure/persistence/GrowthPointStudentOptionRow.java`
- 新增：`server/src/main/resources/mapper/growthpoint/GrowthPointQueryMapper.xml`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointQueryService.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointAccountView.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointLedgerView.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointLedgerPage.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/application/GrowthPointStudentOption.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointQueryController.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointAccountResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointLedgerResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointLedgerPageResponse.java`
- 新增：`server/src/main/java/com/lingdong/learning/growthpoint/web/GrowthPointStudentOptionResponse.java`
- 修改：`server/src/test/java/com/lingdong/learning/learningtask/web/LearningTaskControllerTest.java`

- [ ] 在已有任务闭环用例中先断言学生本人和主家长账户、台账接口、越权对象、错误角色与功能停用。
- [ ] 运行 `mvn -Dtest=LearningTaskControllerTest test`，确认因接口尚不存在而失败。
- [ ] 实现只读 Mapper：账户单查、台账分页与计数、主家长活动主关系学生候选。
- [ ] 实现应用服务：功能开关、客户端、角色、学生状态、主家长对象范围和分页校验。
- [ ] 实现五个 Controller 端点，所有 Long 标识使用字符串序列化。
- [ ] 重跑用例，确认账户余额、台账顺序、来源信息、404/403/409 行为全部通过。

## 任务三：Web 主家长积分台账

**文件：**
- 新增：`web/src/features/growth-points/types.ts`
- 新增：`web/src/features/growth-points/api.ts`
- 新增：`web/src/features/growth-points/GrowthPointPage.tsx`
- 新增：`web/src/features/growth-points/GrowthPointPage.test.tsx`
- 修改：`web/src/api/capability.ts`
- 修改：`web/src/app/App.tsx`
- 修改：`web/src/app/App.test.tsx`
- 修改：`web/src/styles/index.css`

- [ ] 先编写页面测试：主家长入口、孩子候选、账户摘要、台账行、分页与失败重试。
- [ ] 运行目标 Vitest，用例应因页面、API 和能力字段不存在而失败。
- [ ] 实现类型与 API，所有标识保持字符串。
- [ ] 实现积分页面和 App 路由；仅 `PARENT` 且 `growthPointQueryEnabled=true` 时显示菜单。
- [ ] 使用 Lucide 图标、孩子选择框、稳定摘要布局和紧凑台账表格，不加入编辑或统计按钮。
- [ ] 重跑目标测试与 `npm run build`，确认类型和生产构建通过。

## 任务四：uni-app 学生本人积分页

**文件：**
- 新增：`miniapp/src/api/growth-point.ts`
- 新增：`miniapp/src/pages/growth-points/growth-points.vue`
- 修改：`miniapp/src/api/capability.ts`
- 修改：`miniapp/src/pages.json`
- 修改：`miniapp/src/pages/student-home/student-home.vue`

- [ ] 扩展能力类型和积分 API 类型，标识保持字符串。
- [ ] 首页只在开关启用时显示“我的积分”入口。
- [ ] 新页面先校验会话与能力，再并行读取账户和首屏台账；实现下拉刷新、分页加载、空态和重试。
- [ ] 功能关闭时直接返回学生首页且不发送积分业务请求。
- [ ] 运行 `npm run type-check`、`npm run build:h5`、`npm run build:mp-weixin`。

## 任务五：文档、全量回归与视觉验收

**文件：**
- 修改：`docs/design/00-设计文档体系与需求追溯-V1.0.md`
- 修改：`docs/design/01-功能详细设计-FSD-V1.0.md`
- 修改：`docs/design/02-Web与小程序交互说明-V1.0.md`
- 修改：`docs/design/03-系统架构设计-HLD-V1.0.md`
- 修改：`docs/design/04-数据库设计-V1.0.md`
- 修改：`docs/design/05-Flyway迁移规范-V1.0.md`
- 修改：`docs/design/06-API接口设计-V1.0.md`
- 修改：`docs/design/07-权限与安全设计-V1.0.md`
- 修改：`docs/design/10-测试方案与验收用例-V1.0.md`
- 修改：`docs/design/12-当前实现一致性核对-V1.0.md`

- [ ] 全部使用中文记录 V25 已实现范围与未实现边界。
- [ ] 执行后端 `mvn test`、Web 全量 Vitest 与构建、uni-app 类型检查及双构建。
- [ ] 使用本地虚拟接口验收 Web 桌面/窄屏和 uni-app 移动视口，不连接远程服务。
- [ ] 扫描受版本控制文件中的真实凭据、自增主键、V24 过时现状和差异格式问题。

